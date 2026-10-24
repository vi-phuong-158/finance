import { makeGoogleSheetsPort } from '../lib/google-sheets.js';
import { validateAndProcessTransaction } from '../lib/transaction-service.js';

type RequestLike = {
  method?: string;
  headers: Record<string, string | string[] | undefined>;
  body?: unknown;
};
type ResponseLike = {
  status: (statusCode: number) => { json: (body: Record<string, string>) => unknown };
};

export default async function handler(request: RequestLike, response: ResponseLike) {
  const result = await validateAndProcessTransaction(request, process.env, makeGoogleSheetsPort);
  return response.status(result.httpStatus).json(result.body);
}

