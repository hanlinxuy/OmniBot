"""Edit the text-only PPTX fixtures without platform-specific native wheels."""
import copy
import json
import re
import uuid
import zipfile
from xml.etree import ElementTree as ET

P = '{http://schemas.openxmlformats.org/presentationml/2006/main}'
A = '{http://schemas.openxmlformats.org/drawingml/2006/main}'
R = '{http://schemas.openxmlformats.org/officeDocument/2006/relationships}'
REL = '{http://schemas.openxmlformats.org/package/2006/relationships}'
CT = '{http://schemas.openxmlformats.org/package/2006/content-types}'


def presentation(path, action, args):
    with zipfile.ZipFile(path) as archive:
        entries = {i.filename: archive.read(i.filename) for i in archive.infolist()}
    names = sorted((n for n in entries if re.fullmatch(r'ppt/slides/slide\d+\.xml', n)), key=lambda n: int(re.search(r'(\d+)\.xml', n)[1]))
    slides = [ET.fromstring(entries[n]) for n in names]
    if action == 'read':
        return [{'slide': i + 1, 'text': ['\n'.join(t.text or '' for t in shape.iter(A+'t')) for shape in slide.iter(P+'sp')]} for i, slide in enumerate(slides)]
    if action == 'bullet':
        number, text = args
        if not 1 <= int(number) <= len(slides):
            raise ValueError('Slide number outside presentation')
        target = slides[int(number)-1]
        bodies = list(target.iter(P+'txBody'))
        if len(bodies) < 2:
            raise ValueError('Slide has no body text placeholder')
        paragraph = ET.SubElement(bodies[1], A+'p')
        properties = ET.SubElement(paragraph, A+'pPr')
        ET.SubElement(properties, A+'buChar', {'char': '•'})
        run = ET.SubElement(paragraph, A+'r')
        ET.SubElement(run, A+'t').text = text
        entries[names[int(number)-1]] = ET.tostring(target, encoding='utf-8', xml_declaration=True)
    elif action == 'add-slide':
        title, text = args
        new = copy.deepcopy(slides[-1])
        bodies = list(new.iter(P+'txBody'))
        if len(bodies) < 2:
            raise ValueError('Template slide has no body text placeholder')
        for body, content in zip(bodies, (title, text)):
            for paragraph in list(body.findall(A+'p')):
                body.remove(paragraph)
            for line in content.splitlines():
                paragraph = ET.SubElement(body, A+'p')
                run = ET.SubElement(paragraph, A+'r')
                ET.SubElement(run, A+'t').text = line
        number = max(int(re.search(r'(\d+)\.xml', n)[1]) for n in names) + 1
        name = f'ppt/slides/slide{number}.xml'
        entries[name] = ET.tostring(new, encoding='utf-8', xml_declaration=True)
        old_rels = names[-1].replace('slides/', 'slides/_rels/') + '.rels'
        if old_rels in entries:
            entries[name.replace('slides/', 'slides/_rels/')+'.rels'] = entries[old_rels]
        relationships = ET.fromstring(entries['ppt/_rels/presentation.xml.rels'])
        rid = 'rId' + uuid.uuid4().hex
        ET.SubElement(relationships, REL+'Relationship', {'Id': rid, 'Type': R[1:-1]+'/slide', 'Target': f'slides/slide{number}.xml'})
        entries['ppt/_rels/presentation.xml.rels'] = ET.tostring(relationships, encoding='utf-8', xml_declaration=True)
        document = ET.fromstring(entries['ppt/presentation.xml'])
        ids = document.find(P+'sldIdLst')
        ET.SubElement(ids, P+'sldId', {'id': str(max(int(s.attrib['id']) for s in ids)+1), R+'id': rid})
        entries['ppt/presentation.xml'] = ET.tostring(document, encoding='utf-8', xml_declaration=True)
        content = ET.fromstring(entries['[Content_Types].xml'])
        ET.SubElement(content, CT+'Override', {'PartName': '/'+name, 'ContentType': 'application/vnd.openxmlformats-officedocument.presentationml.slide+xml'})
        entries['[Content_Types].xml'] = ET.tostring(content, encoding='utf-8', xml_declaration=True)
    else:
        raise ValueError('Use read, bullet or add-slide for PPTX')
    with zipfile.ZipFile(path, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in entries.items():
            archive.writestr(name, data)
    return {'saved': True, 'file': path.name}
