import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const source = readFileSync(new URL('../../main/resources/static/js/chat.js', import.meta.url), 'utf8')
    .replace(/^import .*;\r?\n/, '');
const origin = 'https://chat.example.invalid';
const attachment = '/uploads/chat/1/u11-123.png';
const uuid = '12345678-abcd-abcd-abcd-123456789abc';

// A deliberately small DOM double: HTML parsing sinks throw, so tests exercise the
// production renderer and DOM properties without a browser or additional dependencies.
class Element {
    constructor(tag) {
        this.tagName = tag.toUpperCase();
        this.children = [];
        this.style = {};
        this.attributes = new Map();
        this.listeners = {};
        this.value = '';
        this.files = [];
        this.scrollHeight = 1000;
        this.scrollTop = 0;
        this.text = '';
    }
    set innerHTML(_) { throw new Error('HTML parsing sink used'); }
    insertAdjacentHTML() { throw new Error('HTML parsing sink used'); }
    set textContent(value) { this.children = []; this.text = String(value); }
    get textContent() { return this.text + this.children.map(child => child.textContent).join(''); }
    set src(value) { this.attributes.set('src', String(value)); }
    get src() { return this.attributes.get('src') || ''; }
    removeAttribute(name) { this.attributes.delete(name); }
    appendChild(child) { this.children.push(child); return child; }
    replaceChildren(...children) { this.children = children; this.text = ''; }
    addEventListener(name, callback) { this.listeners[name] = callback; }
}

function harness() {
    const elements = new Map();
    const byId = id => {
        if (!elements.has(id)) elements.set(id, new Element(id.includes('image') ? 'img' : 'div'));
        return elements.get(id);
    };
    const state = { rooms: [], history: [], sent: [], subscriptions: [], requests: [], revoked: [] };
    class BrowserURL extends URL {
        static createObjectURL() { return 'blob:https://chat.example.invalid/synthetic-preview'; }
        static revokeObjectURL(url) { state.revoked.push(url); }
    }
    const client = {
        connected: false,
        connect(headers, success) { this.connected = true; success(); },
        subscribe(destination, callback) {
            const sub = { destination, callback, unsubscribed: false, unsubscribe() { this.unsubscribed = true; } };
            state.subscriptions.push(sub);
            return sub;
        },
        send(...args) { state.sent.push(args); }
    };
    const context = vm.createContext({
        URL: BrowserURL, console, FormData, File,
        window: { location: { origin, href: origin + '/chat.html' }, history: { replaceState() {} } },
        document: { getElementById: byId, createElement: tag => new Element(tag), addEventListener() {} },
        SockJS: class { constructor(path) { assert.equal(path, '/ws'); } },
        Stomp: { over: () => client },
        apiFetch: async path => path === '/api/chat/rooms' ? state.rooms : { items: state.history },
        initCsrf: async () => ({ data: { headerName: 'X-XSRF-TOKEN', token: 'synthetic-csrf' } }),
        fetch: async (url, options) => { state.requests.push({ url, options }); return { ok: true }; }
    });
    vm.runInContext(source + '\nme = { userId: 11 };', context);
    return { context, state, byId };
}

const images = node => [...(node.tagName === 'IMG' ? [node] : []), ...node.children.flatMap(images)];
const message = overrides => ({ senderUserId: 11, senderName: 'Synthetic sender', type: 'TEXT',
    content: 'Hello room', createdAt: '2026-01-01T00:00:00Z', ...overrides });

for (const malicious of [
    `${attachment}" onerror="globalThis.pwned=true`,
    `${attachment}"><svg onload="globalThis.pwned=true">`,
    'javascript:alert(1)', 'data:image/svg+xml,<svg onload=alert(1)>', 'blob:https://chat.example.invalid/remote',
    'https://external.example.invalid' + attachment, '//external.example.invalid' + attachment,
    'http://chat.example.invalid' + attachment, 'https://chat.example.invalid:444' + attachment,
    'https://user:pass@chat.example.invalid' + attachment,
    '/uploads/profiles/11/profile-123.png', '/uploads/chat/1/unexpected.svg', '/uploads/chat/1/u11-123.svg',
    '/uploads/chat/0/u11-123.png', '/uploads/chat/-1/u11-123.png', '/uploads/chat/9223372036854775808/u11-123.png',
    '/uploads/chat/1/u9223372036854775808-123.png', '/uploads/chat/1/u11-123.png/extra',
    '/other/../uploads/chat/1/u11-123.png', '/uploads/chat/1/%7511-123.png',
    '/uploads/chat/1/u11-123.png?x=1', '/uploads/chat/1/u11-123.png#fragment',
    ' /uploads/chat/1/u11-123.png', '/uploads/chat/1/u11-123.png\n',
    '/uploads\\chat\\1\\u11-123.png', 'https://[malformed', {}, null
]) {
    test(`reject unsafe attachment ${JSON.stringify(malicious)}`, () => {
        const { context, byId } = harness();
        assert.equal(context.chatAttachmentUrl(malicious), null);
        context.renderMessage(message({ type: 'IMAGE', attachmentUrl: malicious }));
        assert.equal(images(byId('chatBox')).length, 0);
    });
}

for (const path of [attachment, origin + attachment, `/uploads/chat/1/u11-${uuid}.jpg`,
    '/uploads/chat/1/u11-123.jpeg', '/uploads/chat/1/u11-123.webp', '/uploads/chat/1/u11-123.PNG']) {
    test(`render supported protected image ${path}`, () => {
        const { context, byId } = harness();
        context.renderMessage(message({ type: 'IMAGE', attachmentUrl: path }));
        const image = images(byId('chatBox'))[0];
        assert.ok(image);
        assert.equal(image.src, new URL(path, origin).href);
        assert.equal(image.alt, 'image');
        assert.deepEqual([...image.attributes.keys()], ['src']);
        assert.equal(byId('chatBox').scrollTop, byId('chatBox').scrollHeight);
    });
}

test('sender, timestamp and message strings stay text with original classes and ordering', () => {
    const { context, byId } = harness();
    const payload = '<img src=x onerror="globalThis.pwned=true">';
    context.renderMessage(message({ senderName: payload, content: payload, createdAt: payload }));
    context.renderMessage(message({ senderUserId: 12, content: 'Second' }));
    const [first, second] = byId('chatBox').children;
    assert.equal(first.className, 'chat-msg me');
    assert.equal(second.className, 'chat-msg');
    assert.equal(first.children[0].className, 'meta');
    assert.ok(first.children[0].textContent.startsWith(payload + ' · '));
    assert.equal(first.children[1].textContent, payload);
    assert.equal(second.children[1].textContent, 'Second');
    assert.equal(images(byId('chatBox')).length, 0);
    assert.equal(context.pwned, undefined);
});

test('room names, snippets and malicious avatar values cannot inject markup', () => {
    const { context } = harness();
    const payload = '<svg onload="globalThis.pwned=true">';
    const card = context.roomCard({ otherName: payload, lastMessage: payload, lastAt: payload,
        otherPicture: `${attachment}" onerror="globalThis.pwned=true` });
    assert.equal(card.className, 'row');
    assert.equal(card.children[0].className, 'avatar');
    assert.equal(card.children[1].children[0].children[0].textContent, payload);
    assert.ok(card.children[1].children[1].textContent.startsWith(payload));
    assert.equal(images(card).length, 0);
});

test('profile avatars preserve current, legacy and OAuth HTTPS sources through properties', () => {
    const { context } = harness();
    for (const path of ['/uploads/profiles/11/profile-123.png', '/uploads/profile-11-123.png',
        `/uploads/profile_11_${uuid}.webp`, 'https://oauth.example.invalid/photo?s=96']) {
        const card = context.roomCard({ otherPicture: path });
        assert.equal(images(card)[0].src, new URL(path, origin).href);
    }
    const fallback = context.roomCard({ otherPic: '/uploads/profiles/11/profile-123.png' });
    assert.equal(images(fallback).length, 1);
    for (const path of ['javascript:alert(1)', 'data:image/png,test', attachment,
        'http://external.example.invalid/photo', '//external.example.invalid/photo',
        'https://user:pass@oauth.example.invalid/photo']) {
        assert.equal(images(context.roomCard({ otherPicture: path })).length, 0);
    }
});

test('selected local files still use blob preview URLs and are cleared and revoked', () => {
    const { context, state, byId } = harness();
    const file = new File(['synthetic'], '<img onerror=alert(1)>.png', { type: 'image/png' });
    context.showImagePreview(file);
    const url = byId('imagePreview').src;
    assert.ok(url.startsWith('blob:'));
    assert.equal(byId('imagePreviewName').textContent, file.name);
    assert.equal(byId('imagePreviewWrap').style.display, 'flex');
    context.hideImagePreview();
    assert.equal(byId('imagePreview').src, '');
    assert.equal(byId('imagePreviewWrap').style.display, 'none');
    assert.deepEqual(state.revoked, [url]);
});

test('room loading, reversed history, switching and incoming STOMP rendering remain functional', async () => {
    const { context, state, byId } = harness();
    state.rooms = [{ roomId: 1, otherName: 'Synthetic room' }, { roomId: 2, otherName: 'Other room' }];
    state.history = [message({ content: 'Newest' }), message({ content: 'Oldest' })];
    await context.loadRooms();
    assert.equal(byId('roomsList').children.length, 2);
    assert.equal(byId('chatBox').children[0].children[1].textContent, 'Oldest');
    assert.equal(byId('chatBox').children[1].children[1].textContent, 'Newest');
    assert.equal(state.subscriptions[0].destination, '/topic/rooms/1');
    await byId('roomsList').children[1].listeners.click();
    assert.equal(state.subscriptions[0].unsubscribed, true);
    assert.equal(state.subscriptions[1].destination, '/topic/rooms/2');
    state.subscriptions[1].callback({ body: JSON.stringify(message({ type: 'IMAGE', attachmentUrl: attachment })) });
    assert.equal(images(byId('chatBox')).length, 1);
});

test('text SEND and image upload preserve destination, form, credentials and CSRF contracts', async () => {
    const { context, state, byId } = harness();
    await context.openRoom(1);
    byId('messageInput').value = ' Synthetic text ';
    await context.sendText();
    assert.equal(state.sent[0][0], '/app/chat.send/1');
    assert.equal(JSON.parse(state.sent[0][2]).content, 'Synthetic text');
    assert.equal(byId('messageInput').value, '');
    const file = new File(['synthetic'], 'synthetic.png', { type: 'image/png' });
    byId('imageInput').files = [file];
    await context.sendImage();
    const { url, options } = state.requests[0];
    assert.equal(url, '/api/chat/rooms/1/image');
    assert.equal(options.method, 'POST');
    assert.equal(options.credentials, 'same-origin');
    assert.equal(options.headers['X-XSRF-TOKEN'], 'synthetic-csrf');
    assert.equal(options.body.get('file').name, 'synthetic.png');
});

test('empty room list renders its existing message safely', async () => {
    const { context, byId } = harness();
    await context.loadRooms();
    assert.equal(byId('roomsList').children[0].className, 'toast info');
    assert.ok(byId('roomsList').textContent.includes('No chats yet.'));
});
