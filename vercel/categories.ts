export const SELF_TRANSFER_KEYWORDS: string[] = [
  'CHUYEN TIEN NOI BO',
];

export const CATEGORY_RULES: Record<string, string[]> = {
  'Chuyển nội bộ': SELF_TRANSFER_KEYWORDS,
  'Siêu thị': ['SUPERMARKET', 'SIÊU THỊ', 'WINMART', 'GO!', 'LOTTE MART', 'LOTTE CINEMA', 'COOPMART', 'BACH HOA XANH', 'AEON', 'MEGA MARKET'],
  'Đi lại': ['GRAB', 'XANH SM', 'GSM', 'BE ', 'TAXI', 'VETC', 'EPASS', 'MAI LINH'],
  'Cafe': ['HIGHLANDS', 'PHUC LONG', 'KATINAT', 'STARBUCKS', 'THE COFFEE HOUSE', 'CONG CAPHE', 'AHA CAFE'],
  'Xăng xe': ['PETROLIMEX', 'PVOIL', 'XANG'],
  'Ăn uống': ['KFC', 'LOTTERIA', 'LOTTE CINEMA', 'JOLIBEE', 'PIZZA', 'GOLDEN GATE', 'HADILAO', 'MANWAH', 'GOGI'],
  'Điện nước & Net': ['EVN', 'DIEN LUC', 'CAP NUOC', 'VIETTEL', 'VNPT', 'FPT', 'MOBIFONE', 'VINAPHONE'],
  'Y tế': ['LONG CHAU', 'PHARMACITY', 'AN KHANG', 'BENH VIEN', 'PHONG KHAM', 'TAM ANH'],
  'Mua sắm': ['SHOPEE', 'LAZADA', 'TIKTOK', 'TIKI', 'UNIQLO', 'DECATHLON'],
};

function normalizeCategoryText(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLocaleUpperCase('vi-VN')
    .replace(/[^\p{L}\p{N}]+/gu, ' ')
    .trim();
}

export function matchCategory(text: string, type: 'THU' | 'CHI'): string {
  const normalized = normalizeCategoryText(text);
  const matches = Object.entries(CATEGORY_RULES)
    .flatMap(([category, keywords]) => keywords
      .filter((keyword) => {
        const normalizedKeyword = normalizeCategoryText(keyword);
        return normalizedKeyword.length > 0 && ` ${normalized} `.includes(` ${normalizedKeyword} `);
      })
      .map((keyword) => ({ category, length: normalizeCategoryText(keyword).length })));
  matches.sort((left, right) => right.length - left.length);
  if (matches.length > 0) return matches[0].category;
  return type === 'THU' ? 'Thu nhập / Nhận CK' : 'Chưa phân loại';
}

