import { memoryIdentityKey, ownAccountDigest } from '../lib/category-memory.js';
import { validWebhookSecret } from '../lib/configuration.js';

async function readAll(stream: NodeJS.ReadableStream): Promise<string> {
  const chunks: Buffer[] = [];
  for await (const chunk of stream) chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  return Buffer.concat(chunks).toString('utf8');
}

async function main() {
  const secret = process.env.CATEGORY_MEMORY_HMAC_KEY ?? '';
  if (!validWebhookSecret(secret)) {
    process.stderr.write('Error: CATEGORY_MEMORY_HMAC_KEY is required and must be a valid secret (32-128 chars).\n');
    process.exit(1);
  }

  const rawInput = await readAll(process.stdin);
  // R5: Normalize input (strip spaces, hyphens, uppercase) and validate 6-32 alphanumeric characters.
  const account = rawInput.replace(/[\s-]+/g, '').toUpperCase();
  if (!account) {
    process.stderr.write('Error: Account number must be provided via stdin.\n');
    process.exit(1);
  }
  if (!/^[A-Z0-9]{6,32}$/.test(account)) {
    process.stderr.write('Error: Invalid account number format. Must be 6-32 alphanumeric characters.\n');
    process.exit(1);
  }

  const hmacKey = memoryIdentityKey(secret);
  const digest = ownAccountDigest(account, hmacKey);
  // Security guarantee: Only the digest is output. Raw account is never echoed.
  process.stdout.write(digest + '\n');
}

main().catch((err) => {
  process.stderr.write(`Error: ${err instanceof Error ? err.message : String(err)}\n`);
  process.exit(1);
});
