export type Category = { id: string; name: string; type: 'THU' | 'CHI' | 'BOTH'; aliases: string[]; keywords: string[]; active: boolean; system: boolean };
export const SELF_TRANSFER_KEYWORDS = ['CHUYEN TIEN NOI BO'];
const EXPENSES: [string, string][] = [
  ['other-expense', 'Các chi phí khác'], ['food', 'Ăn uống'], ['gifts', 'Quà tặng & Quyên góp'],
  ['health', 'Sức khỏe'], ['children', 'Con cái'], ['insurance', 'Bảo hiểm'], ['supermarket', 'Siêu thị'],
  ['water', 'Hoá đơn nước'], ['personal', 'Đồ dùng cá nhân'], ['beauty', 'Làm đẹp'], ['fuel', 'Xăng'],
  ['phone', 'Hoá đơn điện thoại'], ['dining', 'Ăn tiệm'], ['taxi', 'Taxi'], ['shopping', 'Mua sắm'],
  ['transport', 'Di chuyển'], ['cafe', 'Cafe'], ['internet', 'Hoá đơn internet'], ['interest', 'Trả lãi'],
  ['household-service', 'Dịch vụ gia đình'], ['entertainment', 'Giải trí'], ['online-service', 'Dịch vụ trực tuyến'],
  ['checkup', 'Khám sức khoẻ'], ['utilities', 'Hoá đơn & Tiện ích'], ['snacks', 'Ăn vặt'], ['family', 'Gia đình'],
  ['electricity', 'Hoá đơn điện'], ['lending', 'Cho vay'], ['vehicle-maintenance', 'Bảo dưỡng xe'],
  ['ceremonies', 'Hiếu hỷ'], ['household-goods', 'Đồ gia dụng'],
];
const KEYWORDS: Record<string, string[]> = {
  'Chuyển nội bộ': SELF_TRANSFER_KEYWORDS,
  'Siêu thị': ['SUPERMARKET', 'SIÊU THỊ', 'WINMART', 'GO!', 'LOTTE MART', 'COOPMART', 'BACH HOA XANH', 'AEON', 'MEGA MARKET'],
  'Taxi': ['GRAB', 'XANH SM', 'GSM', 'BE', 'TAXI', 'MAI LINH'], 'Di chuyển': ['VETC', 'EPASS'],
  'Cafe': ['HIGHLANDS', 'PHUC LONG', 'KATINAT', 'STARBUCKS', 'THE COFFEE HOUSE', 'CONG CAPHE', 'AHA CAFE'],
  'Xăng': ['PETROLIMEX', 'PVOIL', 'XANG'],
  'Ăn uống': ['KFC', 'LOTTERIA', 'JOLIBEE', 'PIZZA', 'GOLDEN GATE', 'HADILAO', 'MANWAH', 'GOGI'],
  'Giải trí': ['LOTTE CINEMA'], 'Hoá đơn điện': ['EVN', 'DIEN LUC'], 'Hoá đơn nước': ['CAP NUOC'],
  'Hoá đơn internet': ['VNPT', 'FPT'], 'Hoá đơn điện thoại': ['VIETTEL', 'MOBIFONE', 'VINAPHONE'],
  'Sức khỏe': ['LONG CHAU', 'PHARMACITY', 'AN KHANG', 'BENH VIEN', 'PHONG KHAM', 'TAM ANH'],
  'Mua sắm': ['SHOPEE', 'LAZADA', 'TIKTOK', 'TIKI', 'UNIQLO', 'DECATHLON'], 'Lương': ['LUONG', 'SALARY', 'PAYROLL'],
};
const ALIASES: Record<string, string[]> = {
  'Di chuyển': ['Đi lại'], 'Xăng': ['Xăng xe'], 'Sức khỏe': ['Y tế'],
  'Hoá đơn & Tiện ích': ['Điện nước & Net'], 'Tiền chuyển đến': ['Thu nhập / Nhận CK'],
};
function category(id: string, name: string, type: Category['type'], system = false): Category {
  return { id, name, type, aliases: ALIASES[name] ?? [], keywords: KEYWORDS[name] ?? [], active: true, system };
}
export const CATEGORY_REGISTRY: Category[] = [
  ...EXPENSES.map(([id, name]) => category(id, name, 'CHI')),
  category('incoming-transfer', 'Tiền chuyển đến', 'THU'), category('other-income', 'Thu nhập khác', 'THU'), category('salary', 'Lương', 'THU'),
  category('internal-transfer', 'Chuyển nội bộ', 'BOTH', true), category('uncategorized', 'Chưa phân loại', 'BOTH', true),
];
export const CATEGORY_RULES: Record<string, string[]> = Object.fromEntries(CATEGORY_REGISTRY.map((c) => [c.name, c.keywords]));
export function normalizeCategoryText(text: string): string {
  return text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[đĐ]/g, 'd').toUpperCase().replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
}
export function canonicalBankCategory(name: string): string {
  return CATEGORY_REGISTRY.find((c) => c.name === name || c.aliases.includes(name))?.name ?? name;
}
export function matchCategory(text: string, type: 'THU' | 'CHI'): string {
  const normalized = ` ${normalizeCategoryText(text)} `;
  const internal = CATEGORY_REGISTRY.find((c) => c.id === 'internal-transfer')!;
  const matches = (c: Category) => c.keywords.filter((k) => normalized.includes(` ${normalizeCategoryText(k)} `));
  if (matches(internal).length) return internal.name;
  const candidates = CATEGORY_REGISTRY.filter((c) => c.active && c.type === type)
    .flatMap((c) => matches(c).map((keyword) => ({ name: c.name, length: normalizeCategoryText(keyword).length })))
    .sort((a, b) => b.length - a.length);
  return candidates[0]?.name ?? (type === 'THU' ? 'Tiền chuyển đến' : 'Chưa phân loại');
}
