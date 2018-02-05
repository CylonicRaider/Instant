#!/usr/bin/env python3
# -*- coding: ascii -*-

# A script removing animations from SVG graphics.

import sys, os, re

# etree fails utterly at producing nice-looking XML
from xml.dom import minidom

def process(inpt, outp):
    def traverse(node):
        for child in node.childNodes:
            if child.nodeType != minidom.Node.ELEMENT_NODE:
                continue
            elif child.tagName in ('animate', 'animateTransform'):
                node.removeChild(child)
            elif child.tagName in ('style', 'script'):
                if child.getAttribute('key') == 'animation':
                    node.removeChild(child)
            else:
                traverse(child)
        node.normalize()
        if len(node.childNodes) == 0: return
        for child in (node.childNodes[0], node.childNodes[-1]):
            if child.nodeType != minidom.Node.TEXT_NODE:
                continue
            if not child.data.isspace() or child.data.count('\n') <= 1:
                continue
            if len(node.childNodes) == 1:
                node.removeChild(child)
                return
            child.data = re.sub(r'\n.*\n', r'\n', child.data)
    document = minidom.parse(inpt)
    traverse(document.documentElement)
    outp.write('<?xml version="1.0" encoding="utf-8"?>\n')
    document.documentElement.writexml(outp)
    outp.write('\n')

def main():
    if len(sys.argv) != 3:
        sys.stderr.write('USAGE: %s input output\n' % sys.argv[0])
        sys.stderr.flush()
        sys.exit(0)
    with open(sys.argv[1]) as inpt, open(sys.argv[2], 'w') as outp:
        process(inpt, outp)

if __name__ == '__main__': main()
