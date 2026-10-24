import type { ParsedTransaction } from './vcb.js';

export function parseBidv(_rawText: string): ParsedTransaction | null {
  // Giai đoạn đầu trả về null để tự động lưu mẫu thực tế vào tab Raw_Capture trên Google Sheets.
  // Sau khi có mẫu thật trong Raw_Capture, bổ sung Regex tương tự vcb.ts tại đây.
  return null;
}

