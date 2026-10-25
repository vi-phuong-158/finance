"""Read-only OOXML reader. Python stdlib only; never evaluates formulas or extracts ZIP paths."""
import json
import posixpath
import re
import sys
import zipfile
import xml.etree.ElementTree as ET

NS = {'s': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
REL = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'


def read_xlsx(path):
    with zipfile.ZipFile(path) as archive:
        infos = archive.infolist()
        if len(infos) > 2000 or sum(i.file_size for i in infos) > 32 * 1024 * 1024:
            raise ValueError('XLSX_TOO_LARGE')
        if len({i.filename for i in infos}) != len(infos):
            raise ValueError('INVALID_XLSX')

        def xml(name):
            data = archive.read(name)
            if b'<!DOCTYPE' in data.upper() or b'<!ENTITY' in data.upper():
                raise ValueError('INVALID_XLSX')
            return ET.fromstring(data)

        workbook = xml('xl/workbook.xml')
        sheets = [s for s in workbook.findall('s:sheets/s:sheet', NS) if s.get('name') == 'Transaction']
        if len(sheets) != 1:
            raise ValueError('TRANSACTION_SHEET_REQUIRED')
        rid = sheets[0].get('{' + REL + '}id')
        rels = xml('xl/_rels/workbook.xml.rels')
        targets = [r for r in rels if r.get('Id') == rid and r.get('TargetMode') != 'External']
        if len(targets) != 1:
            raise ValueError('INVALID_XLSX')
        target = targets[0].get('Target', '')
        name = posixpath.normpath(target.lstrip('/') if target.startswith('/') else 'xl/' + target)
        if not name.startswith('xl/worksheets/'):
            raise ValueError('INVALID_XLSX')
        strings = []
        if 'xl/sharedStrings.xml' in archive.namelist():
            strings = [''.join(t.text or '' for t in si.iter('{' + NS['s'] + '}t'))
                       for si in xml('xl/sharedStrings.xml').findall('s:si', NS)]
        rows = []
        for row in xml(name).findall('s:sheetData/s:row', NS):
            row_number = int(row.get('r', '0'))
            if not 1 <= row_number <= 50001:
                raise ValueError('XLSX_TOO_LARGE')
            cells = []
            seen = set()
            for cell in row.findall('s:c', NS):
                ref = re.fullmatch(r'([A-Z]+)([1-9][0-9]*)', cell.get('r', ''))
                if not ref or int(ref[2]) != row_number or ref[1] in seen:
                    raise ValueError('INVALID_XLSX')
                seen.add(ref[1])
                column = 0
                for char in ref[1]:
                    column = column * 26 + ord(char) - 64
                if column > 100:
                    raise ValueError('XLSX_TOO_LARGE')
                while len(cells) < column:
                    cells.append(None)
                if cell.find('s:f', NS) is not None:
                    raise ValueError('FORMULA_NOT_ALLOWED')
                kind = cell.get('t', 'n')
                value = cell.find('s:v', NS)
                text = value.text if value is not None else None
                if kind == 's':
                    value = strings[int(text)]
                elif kind == 'inlineStr':
                    value = ''.join(t.text or '' for t in cell.findall('s:is//s:t', NS))
                elif kind == 'b' and text in ('0', '1'):
                    value = text == '1'
                elif kind in ('str', 'd'):
                    value = text or ''
                elif kind == 'n':
                    value = float(text) if text else None
                else:
                    raise ValueError('UNSUPPORTED_CELL_TYPE')
                cells[column - 1] = value
            rows.append({'rowNumber': row_number, 'cells': cells})
        props = workbook.find('s:workbookPr', NS)
        return {'rows': rows, 'date1904': props is not None and props.get('date1904') in ('true', '1')}


if __name__ == '__main__':
    try:
        if len(sys.argv) != 2 or not sys.argv[1].lower().endswith('.xlsx'):
            raise ValueError('XLSX_FILE_REQUIRED')
        print(json.dumps(read_xlsx(sys.argv[1]), ensure_ascii=True, allow_nan=False))
    except Exception as error:
        allowed = {'XLSX_TOO_LARGE', 'INVALID_XLSX', 'TRANSACTION_SHEET_REQUIRED',
                   'FORMULA_NOT_ALLOWED', 'UNSUPPORTED_CELL_TYPE', 'XLSX_FILE_REQUIRED'}
        print(str(error) if str(error) in allowed else 'XLSX_READ_FAILED', file=sys.stderr)
        sys.exit(1)
