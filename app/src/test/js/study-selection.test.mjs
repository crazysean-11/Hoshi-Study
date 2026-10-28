import assert from 'node:assert/strict';
import test from 'node:test';
import fs from 'node:fs';
import vm from 'node:vm';

const script = fs.readFileSync(new URL('../../main/assets/hoshi-web/reader/study-selection.js', import.meta.url), 'utf8');
function selected(nodes, options = {}) {
    const fragment = {
        querySelectorAll(selector) { return nodes.filter(n => selector.split(', ').includes(n.tag)).map(n => ({ remove() { n.removed = true; } })); },
        get textContent() { return nodes.filter(n => !n.removed).map(n => n.text).join(''); },
    };
    const selection = { rangeCount: 1, isCollapsed: false, getRangeAt() { return { cloneContents() { return fragment; } }; }, ...options };
    return vm.runInNewContext(script, { window: { getSelection() { return selection; } } });
}
test('study translation excludes ruby readings, brackets and script text', () => {
    assert.equal(selected([{ tag: 'span', text: '彼は漢字' }, { tag: 'rp', text: '(' }, { tag: 'rt', text: 'かんじ' }, { tag: 'rp', text: ')' }, { tag: 'span', text: 'を読んだ。' }, { tag: 'script', text: 'bad' }]), '彼は漢字を読んだ。');
});
test('study selection preserves punctuation, line breaks and supplementary characters', () => {
    assert.equal(selected([{ tag: 'span', text: ' 「𠮷野さん、\n行こう。」 ' }]), '「𠮷野さん、\n行こう。」');
});
test('empty or collapsed selection never submits a passage', () => {
    assert.equal(selected([], { rangeCount: 0 }), '');
    assert.equal(selected([{ tag: 'span', text: '言葉' }], { isCollapsed: true }), '');
});
test('bounds selected text to avoid sending an entire book', () => {
    assert.equal(selected([{ tag: 'span', text: '本'.repeat(8000) }]).length, 6000);
});
test('the selection limit never splits a supplementary character', () => {
    const text = selected([{ tag: 'span', text: '本'.repeat(5999) + '𠮷野' }]);
    assert.equal(Array.from(text).length, 6000);
    assert.ok(text.endsWith('𠮷'));
});
