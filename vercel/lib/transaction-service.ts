import { createEventId, normalizeTransactionText } from '../event-identity.js';
import { maskSensitiveIdentifiers } from '../sensitive-identifiers.js';
export { normalizeTransactionText } from '../event-identity.js';
import { matchCategory } from '../categories.js';
import { parseBidv } from '../parsers/bidv.js';
import { parseVcb } from '../parsers/vcb.js';
import { parseVietinbank } from '../parsers/vietinbank.js';
import { timingSafeEqual } from 'node:crypto';
import { configurationIsValid, readGoogleConfiguration } from './configuration.js';
import { hasExpectedHeaders, hasTransactionHeaders, TRANSACTION_HEADERS, RAW_CAPTURE_HEADERS } from './sheet-schema.js';
export { TRANSACTION_HEADERS, RAW_CAPTURE_HEADERS } from './sheet-schema.js';

export type Bank = 'VCB' | 'VIETINBANK' | 'BIDV';
type RequestLike = {
  method?: string;
  headers?: Record<string, string | string[] | undefined>;
  body?: unknown;
};
type Environment = Record<string, string | undefined>;
type SheetsPort = {
  readEventIds: () => Promise<{ transactionIds: unknown[]; rawIds: unknown[] }>;
  readHeaders: () => Promise<{ transaction: unknown[]; raw: unknown[] }>;
  appendTransaction: (row: unknown[]) => Promise<void | 'APPENDED' | 'DUPLICATE'>;
  appendRaw: (row: unknown[]) => Promise<void | 'APPENDED' | 'DUPLICATE'>;
};
type SheetsPortFactory = (config: { spreadsheetId: string; clientEmail: string; privateKey: string }) => Promise<SheetsPort>;
export type TransactionResult = {
  httpStatus: number;
  body: Record<string, string>;
};

const BANK_PACKAGES: Record<Bank, string> = {
  VCB: 'com.VCB',
  VIETINBANK: 'com.vietinbank.ipay',
  BIDV: 'com.vnpay.bidv',
};

const SENSITIVE_BLACKLIST = /\b(otp|smart\s*otp|soft\s*otp|ma\s*xac\s*thuc|ma\s*kich\s*hoat|ma\s*bao\s*mat|verification\s*code|activation\s*code|passcode|khong\s*cung\s*cap\s*ma|khong\s*chia\s*se\s*ma|dang\s*nhap|thiet\s*bi\s*moi|mat\s*khau|doi\s*mat\s*khau|kich\s*hoat|sinh\s*trac\s*hoc|khoa\s*the|mo\s*khoa\s*the|cvv|so\s*the|han\s*muc\s*tin\s*dung|bao\s*tri\s*he\s*thong|khuyen\s*mai|uu\s*dai)\b/i;
const MONEY_SIGNAL = /(?:\b(?:VND|VNĐ)\b|[+-]\s*\d[\d.,]{2,})/i;
const ACCOUNT_SIGNAL = /\b(so\s*du|sd|tk|tai\s*khoan|gd|giao\s*dich|ps|phat\s*sinh|chuyen\s*khoan|thanh\s*toan)\b/i;

function removeAccents(value: string): string {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D');
}

function isBank(value: unknown): value is Bank {
  return value === 'VCB' || value === 'VIETINBANK' || value === 'BIDV';
}

function stringHeader(value: string | string[] | undefined): string {
  return Array.isArray(value) ? value[0] ?? '' : value ?? '';
}

function isValidTimestamp(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0 && Number.isFinite(new Date(value).getTime());
}

function formatReceivedTime(postedAt: number): string {
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Ho_Chi_Minh',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(new Date(postedAt)).replace(',', '');
}

export function validateAndProcessTransaction(request: RequestLike, env: Environment, makeSheetsPort: SheetsPortFactory): Promise<TransactionResult> {
  return validateAndProcessTransactionInternal(request, env, makeSheetsPort);
}

async function validateAndProcessTransactionInternal(
  request: RequestLike,
  env: Environment,
  makeSheetsPort: SheetsPortFactory,
): Promise<TransactionResult> {
  if (request.method !== 'POST') return { httpStatus: 405, body: { error: 'METHOD_NOT_ALLOWED' } };
  if (!configurationIsValid(env)) return { httpStatus: 503, body: { error: 'SERVER_CONFIGURATION_ERROR' } };

  const suppliedSecret = stringHeader(request.headers?.['x-webhook-secret']);
  const suppliedBytes = Buffer.from(suppliedSecret);
  const expectedBytes = Buffer.from(env.WEBHOOK_SECRET!);
  if (suppliedBytes.length !== expectedBytes.length || !timingSafeEqual(suppliedBytes, expectedBytes)) {
    return { httpStatus: 401, body: { error: 'UNAUTHORIZED' } };
  }

  const contentType = stringHeader(request.headers?.['content-type']);
  if (!/^application\/json(?:\s*;|$)/i.test(contentType)) {
    return { httpStatus: 415, body: { error: 'UNSUPPORTED_MEDIA_TYPE' } };
  }

  if (!request.body || typeof request.body !== 'object' || Array.isArray(request.body)) {
    return { httpStatus: 400, body: { error: 'INVALID_PAYLOAD' } };
  }

  const payload = request.body as Record<string, unknown>;
  const { eventId, bank, packageName, title = '', text, postedAt, dryRun = false } = payload;
  if (typeof eventId !== 'string' || typeof title !== 'string' || typeof text !== 'string' || typeof packageName !== 'string' || !isBank(bank)) {
    return { httpStatus: 400, body: { error: 'INVALID_PAYLOAD' } };
  }

  if (!/^[a-f0-9]{64}$/.test(eventId) || packageName !== BANK_PACKAGES[bank] || !isValidTimestamp(postedAt) || typeof dryRun !== 'boolean') {
    return { httpStatus: 400, body: { error: 'INVALID_PAYLOAD' } };
  }

  if (Buffer.byteLength(title, 'utf8') > 1024 || Buffer.byteLength(text, 'utf8') > 8192) {
    return { httpStatus: 413, body: { error: 'PAYLOAD_TOO_LARGE' } };
  }

  const normalizedText = normalizeTransactionText(text);
  if (!normalizedText || Buffer.byteLength(normalizedText, 'utf8') > 8192) {
    return { httpStatus: normalizedText ? 413 : 400, body: { error: normalizedText ? 'PAYLOAD_TOO_LARGE' : 'INVALID_PAYLOAD' } };
  }

  const canonicalPayload = JSON.stringify({ eventId, bank, packageName, title, text: normalizedText, postedAt, dryRun });
  if (Buffer.byteLength(canonicalPayload, 'utf8') > 16 * 1024) {
    return { httpStatus: 413, body: { error: 'PAYLOAD_TOO_LARGE' } };
  }

  if (createEventId(bank, postedAt, normalizedText) !== eventId) {
    return { httpStatus: 400, body: { error: 'EVENT_ID_MISMATCH' } };
  }

  const checkedText = removeAccents(`${title} ${normalizedText}`);
  if (SENSITIVE_BLACKLIST.test(checkedText)) {
    return { httpStatus: 200, body: { status: 'DROPPED_SENSITIVE', eventId } };
  }

  if (!MONEY_SIGNAL.test(normalizedText) || !ACCOUNT_SIGNAL.test(checkedText)) {
    return { httpStatus: 200, body: { status: 'DROPPED_NON_TRANSACTION', eventId } };
  }

  const parsed = bank === 'VCB'
    ? parseVcb(normalizedText)
    : bank === 'VIETINBANK'
      ? parseVietinbank(normalizedText)
      : parseBidv(normalizedText);

  let sheets: SheetsPort;
  try {
    sheets = await makeSheetsPort(readGoogleConfiguration(env)!);
  } catch {
    return { httpStatus: 503, body: { error: 'UPSTREAM_UNAVAILABLE' } };
  }

  try {
    const headers = await sheets.readHeaders();
    if (!hasTransactionHeaders(headers.transaction) || !hasExpectedHeaders(headers.raw, RAW_CAPTURE_HEADERS)) {
      return { httpStatus: 503, body: { error: 'SHEET_SCHEMA_MISMATCH' } };
    }

    if (dryRun) {
      return {
        httpStatus: 200,
        body: { status: parsed ? 'VALIDATED_TRANSACTION' : 'VALIDATED_RAW_CAPTURE', eventId },
      };
    }

    const existingIds = await sheets.readEventIds();
    if (existingIds.transactionIds.includes(eventId) || existingIds.rawIds.includes(eventId)) {
      return { httpStatus: 200, body: { status: 'DUPLICATE_IGNORED', eventId } };
    }

    if (parsed) {
      const category = matchCategory(`${parsed.counterparty} ${parsed.description}`, parsed.type);
      const saved = await sheets.appendTransaction([
        parsed.time,
        bank,
        parsed.type,
        parsed.amount,
        parsed.balance,
        maskSensitiveIdentifiers(parsed.counterparty),
        maskSensitiveIdentifiers(parsed.description),
        category,
        eventId,
      ]);

      return { httpStatus: 200, body: { status: saved === 'DUPLICATE' ? 'DUPLICATE_IGNORED' : 'SAVED_TRANSACTION', eventId } };
    }

    const safeText = maskSensitiveIdentifiers(normalizedText);
    const saved = await sheets.appendRaw([formatReceivedTime(postedAt), bank, safeText, eventId]);
    return { httpStatus: 200, body: { status: saved === 'DUPLICATE' ? 'DUPLICATE_IGNORED' : 'SAVED_RAW_CAPTURE', eventId } };
  } catch (error) {
    const code = (error as { response?: { status?: number } })?.response?.status;
    if (code === 401 || code === 403) return { httpStatus: 503, body: { error: 'SHEETS_PERMISSION_DENIED' } };
    if (code === 404) return { httpStatus: 503, body: { error: 'SHEET_NOT_FOUND' } };
    return { httpStatus: 503, body: { error: 'UPSTREAM_UNAVAILABLE' } };
  }
}

