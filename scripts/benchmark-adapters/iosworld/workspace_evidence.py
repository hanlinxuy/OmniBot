"""Normalize documented seed defaults without ignoring genuine cell changes."""
from copy import deepcopy


def normalized_spreadsheets(spreadsheets):
    result = deepcopy(spreadsheets)
    for spreadsheet in result:
        for sheet in spreadsheet['sheets']:
            for cell in sheet['cells']:
                # The source MCP setter writes a duplicate compatibility alias.
                if 'value' in cell and cell['value'] == cell['rawValue']:
                    del cell['value']
                if cell.get('style') is None:
                    # iOSWorld SpreadsheetCell.init(from:) and SpreadsheetCellStyle().
                    cell['style'] = {'bold': False, 'alignment': 'leading', 'fill': 'none'}
    return result
