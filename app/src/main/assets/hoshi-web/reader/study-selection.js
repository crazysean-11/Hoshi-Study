(function () {
    'use strict';
    var selection = window.getSelection();
    if (!selection || !selection.rangeCount || selection.isCollapsed) return '';
    var fragment = selection.getRangeAt(0).cloneContents();
    // Ruby pronunciation is annotation, not part of the sentence being translated.
    fragment.querySelectorAll('rt, rp, script, style').forEach(function (node) { node.remove(); });
    return Array.from((fragment.textContent || '').trim()).slice(0, 6000).join('');
})();
