"""
Read sheet1 of a .xlsx (no openpyxl; uses OOXML) and emit MySQL INSERT for products.
Usage:
  python excel_to_products_sql.py [path/to/file.xlsx] > products_inserts.sql
  python excel_to_products_sql.py --lowercase path/to/file.xlsx   # column names lower_snake_case

Floats for Prix_USD, Prix_net_USD, Remise_pct, Rating are rounded for clean SQL.
"""
import zipfile
import xml.etree.ElementTree as ET
from collections import defaultdict
import re
import sys

DEFAULT_XLSX = r"c:\Users\Legion\Downloads\catalogue_100_produits_categories_images_checked(1).xlsx"
NS = {"x": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}


def col_row(ref):
    m = re.match(r"([A-Z]+)(\d+)", ref)
    if not m:
        return None, None
    return m.group(1), int(m.group(2))


def col_to_idx(col):
    n = 0
    for c in col:
        n = n * 26 + (ord(c) - ord("A") + 1)
    return n - 1


def sql_escape(s):
    if s is None:
        return "NULL"
    return "'" + str(s).replace("\\", "\\\\").replace("'", "''") + "'"


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    path = args[0] if args else DEFAULT_XLSX
    z = zipfile.ZipFile(path)
    root = ET.fromstring(z.read("xl/worksheets/sheet1.xml"))
    rows_data = defaultdict(dict)

    for c in root.findall(".//x:sheetData/x:row/x:c", NS):
        ref = c.get("r")
        if not ref:
            continue
        col_letters, row_num = col_row(ref)
        if row_num is None:
            continue
        t = c.get("t")
        val = None
        if t == "inlineStr":
            is_ = c.find("x:is", NS)
            if is_ is not None:
                parts = []
                for t_el in is_.iter():
                    if t_el.tag.split("}")[-1] == "t":
                        if t_el.text:
                            parts.append(t_el.text)
                        if t_el.tail:
                            parts.append(t_el.tail)
                val = "".join(parts)
        else:
            v = c.find("x:v", NS)
            if v is not None and v.text is not None:
                tx = v.text
                try:
                    if "." in tx or "e" in tx.lower():
                        val = float(tx)
                    else:
                        val = int(tx)
                except ValueError:
                    val = tx
        rows_data[row_num][col_letters] = val

    if 1 not in rows_data:
        print("No header row", file=sys.stderr)
        sys.exit(1)

    col_keys = sorted(rows_data[1].keys(), key=col_to_idx)

    def h_to_sql(h):
        return str(h).strip().replace(" ", "_").replace("-", "_")

    sql_cols = [h_to_sql(rows_data[1][ck]) for ck in col_keys]
    lowercase = "--lowercase" in sys.argv
    if lowercase:
        sql_cols = [c.lower() for c in sql_cols]

    def format_cell(col_name: str, v):
        if v is None or v == "":
            return "NULL"
        if not isinstance(v, (int, float)):
            return sql_escape(v)
        name = col_name.lower()
        if isinstance(v, float) and abs(v - round(v)) < 1e-9:
            return str(int(round(v)))
        if name in ("prix_usd", "prix_net_usd", "rating"):
            return f"{float(v):.2f}"
        if name == "remise_pct":
            return f"{float(v):.5f}"
        if name == "stock" or name == "nb_images":
            return str(int(round(float(v))))
        return str(v)

    print("-- Generated from:", path)
    print("INSERT INTO products (" + ", ".join(sql_cols) + ") VALUES")

    data_rows = [r for r in sorted(rows_data.keys()) if r > 1]
    lines_out = []
    for r in data_rows:
        vals = []
        for i, ck in enumerate(col_keys):
            v = rows_data[r].get(ck)
            col_name = sql_cols[i]
            vals.append(format_cell(col_name, v))
        lines_out.append("(" + ", ".join(vals) + ")")

    print(",\n".join(lines_out) + ";")
    print(f"-- Rows: {len(lines_out)}")


if __name__ == "__main__":
    main()
