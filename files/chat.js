/**
 * ChatWave — Real-Time Chat Client
 * Uses SockJS + STOMP over Spring Boot WebSocket broker
 */
(function () {
    'use strict';

    // ── State ──────────────────────────────────────────────────────────────────
    let stompClient = null;
    let currentUser = null;
    let currentRoom = null;
    let subscriptions = {};
    let typingTimer = null;
    let isTyping = false;
    let typingUsers = {};   // username → timer
    let rooms = [];

    // ── DOM refs ───────────────────────────────────────────────────────────────
    const $ = id => document.getElementById(id);

    const UI = {
        loginScreen:     $('login-screen'),
        appScreen:       $('app-screen'),
        usernameInput:   $('username-input'),
        joinBtn:         $('join-btn'),
        connStatus:      $('conn-status'),
        connDot:         () => $('conn-status').querySelector('.status-dot'),
        connText:        () => $('conn-status').querySelector('.status-text'),
        roomList:        $('room-list'),
        roomTitle:       $('room-title'),
        roomDesc:        $('room-description'),
        roomOnline:      $('room-online-count'),
        messages:        $('messages'),
        emptyState:      $('empty-state'),
        typingBar:       $('typing-bar'),
        typingText:      $('typing-text'),
        messageInput:    $('message-input'),
        sendBtn:         $('send-btn'),
        charCount:       $('char-count'),
        sidebarUsername: $('sidebar-username'),
        userAvatar:      $('user-avatar'),
        totalOnline:     $('total-online'),
        usersList:       $('users-list'),
    };

    // ── Avatar colors ──────────────────────────────────────────────────────────
    const AV_CLASSES = ['av-0', 'av-1', 'av-2', 'av-3', 'av-4'];
    function avatarClass(name) {
        let h = 0;
        for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) & 0xffffffff;
        return AV_CLASSES[Math.abs(h) % AV_CLASSES.length];
    }

    // ── Time formatting ────────────────────────────────────────────────────────
    function formatTime(ts) {
        const d = ts ? new Date(ts) : new Date();
        return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }

    // ── Connection ─────────────────────────────────────────────────────────────
    function connect(username) {
        setConnectionStatus('connecting');
        const socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);
        stompClient.debug = null; // silence STOMP logs

        stompClient.connect({}, () => {
            console.log('✓ WebSocket connected');
            setConnectionStatus('connected');
            currentUser = username;
            showApp();
            loadRooms();
            subscribeToGlobal();
        }, (error) => {
            console.error('WebSocket error:', error);
            setConnectionStatus('disconnected');
            setTimeout(() => connect(username), 3000); // auto-reconnect
        });
    }

    function setConnectionStatus(state) {
        const dot = UI.connDot();
        const text = UI.connText();
        dot.className = 'status-dot ' + state;
        text.textContent = state;
    }

    // ── Global subscriptions (not room-specific) ───────────────────────────────
    function subscribeToGlobal() {
        // User-specific error queue
        stompClient.subscribe('/user/queue/errors', (msg) => {
            const err = JSON.parse(msg.body);
            console.error('Server error:', err.message);
        });

        // User-specific message history (sent on join)
        stompClient.subscribe('/user/queue/history', (msg) => {
            const data = JSON.parse(msg.body);
            renderHistory(data.messages);
        });

        // Global online count
        stompClient.subscribe('/topic/online-count', (msg) => {
            const data = JSON.parse(msg.body);
            UI.totalOnline.textContent = data.count;
        });
    }

    // ── Load Rooms via REST ────────────────────────────────────────────────────
    function loadRooms() {
        fetch('/api/rooms')
            .then(r => r.json())
            .then(data => {
                rooms = data;
                renderRoomList();
                if (rooms.length > 0) joinRoom(rooms[0].id);
            })
            .catch(e => console.error('Failed to load rooms:', e));
    }

    function renderRoomList() {
        UI.roomList.innerHTML = '';
        rooms.forEach(room => {
            const el = document.createElement('div');
            el.className = 'room-item';
            el.dataset.roomId = room.id;
            el.innerHTML = `
                <span class="room-item-name">${escHtml(room.name)}</span>
                ${room.onlineCount > 0 ? `<span class="room-badge">${room.onlineCount}</span>` : ''}
            `;
            el.addEventListener('click', () => joinRoom(room.id));
            UI.roomList.appendChild(el);
        });
    }

    // ── Join Room ──────────────────────────────────────────────────────────────
    function joinRoom(roomId) {
        if (currentRoom === roomId) return;

        // Leave old room
        if (currentRoom) {
            leaveCurrentRoom();
        }

        currentRoom = roomId;
        const room = rooms.find(r => r.id === roomId);

        // Update header
        UI.roomTitle.textContent = room ? room.name : '#' + roomId;
        UI.roomDesc.textContent  = room ? room.description : '';
        UI.messageInput.placeholder = `Message ${room ? room.name : '#' + roomId}…`;
        UI.messageInput.disabled = false;
        UI.sendBtn.disabled = false;

        // Highlight active room
        document.querySelectorAll('.room-item').forEach(el => {
            el.classList.toggle('active', el.dataset.roomId === roomId);
        });

        // Clear messages
        clearMessages();

        // Subscribe to room topics
        subscribeToRoom(roomId);

        // Send JOIN via WebSocket
        stompClient.send('/app/chat.join', {}, JSON.stringify({
            username: currentUser,
            roomId: roomId
        }));
    }

    function leaveCurrentRoom() {
        if (!currentRoom) return;
        stompClient.send('/app/chat.leave', {}, JSON.stringify({
            username: currentUser,
            roomId: currentRoom
        }));

        // Unsubscribe from old room topics
        Object.values(subscriptions).forEach(sub => sub.unsubscribe());
        subscriptions = {};
        typingUsers = {};
    }

    function subscribeToRoom(roomId) {
        // Main message stream
        subscriptions.messages = stompClient.subscribe(
            '/topic/room/' + roomId, (msg) => {
                const payload = JSON.parse(msg.body);
                appendMessage(payload);
            }
        );

        // Typing indicator stream
        subscriptions.typing = stompClient.subscribe(
            '/topic/room/' + roomId + '/typing', (msg) => {
                const event = JSON.parse(msg.body);
                handleTypingEvent(event);
            }
        );

        // Online users stream
        subscriptions.users = stompClient.subscribe(
            '/topic/room/' + roomId + '/users', (msg) => {
                const data = JSON.parse(msg.body);
                renderUsersList(data.users);
                UI.roomOnline.textContent = data.users.length;
                // Update room badge
                const badge = document.querySelector(`.room-item[data-room-id="${roomId}"] .room-badge`);
                if (badge) badge.textContent = data.users.length;
            }
        );
    }

    // ── Send Message ───────────────────────────────────────────────────────────
    function sendMessage() {
        const content = UI.messageInput.value.trim();
        if (!content || !currentRoom) return;

        stompClient.send('/app/chat.send', {}, JSON.stringify({
            content: content,
            sender: currentUser,
            roomId: currentRoom
        }));

        UI.messageInput.value = '';
        updateCharCount();
        stopTyping();
    }

    // ── Typing Indicator ───────────────────────────────────────────────────────
    function sendTyping(typing) {
        if (!currentRoom) return;
        stompClient.send('/app/chat.typing', {}, JSON.stringify({
            username: currentUser,
            roomId: currentRoom,
            typing: typing
        }));
    }

    function startTyping() {
        if (!isTyping) {
            isTyping = true;
            sendTyping(true);
        }
        clearTimeout(typingTimer);
        typingTimer = setTimeout(stopTyping, 2500);
    }

    function stopTyping() {
        if (isTyping) {
            isTyping = false;
            sendTyping(false);
        }
        clearTimeout(typingTimer);
    }

    function handleTypingEvent(event) {
        if (event.username === currentUser) return; // ignore self

        if (event.typing) {
            typingUsers[event.username] = true;
        } else {
            delete typingUsers[event.username];
        }

        updateTypingUI();
    }

    function updateTypingUI() {
        const users = Object.keys(typingUsers);
        if (users.length === 0) {
            UI.typingBar.classList.remove('active');
            UI.typingText.textContent = '';
        } else {
            UI.typingBar.classList.add('active');
            if (users.length === 1) {
                UI.typingText.textContent = users[0] + ' is typing…';
            } else if (users.length === 2) {
                UI.typingText.textContent = users.join(' and ') + ' are typing…';
            } else {
                UI.typingText.textContent = `${users.length} people are typing…`;
            }
        }
    }

    // ── Render Messages ────────────────────────────────────────────────────────
    function appendMessage(msg) {
        removeEmptyState();

        if (msg.type === 'JOIN' || msg.type === 'LEAVE' || msg.type === 'SYSTEM') {
            appendSystemMessage(msg);
        } else {
            appendChatMessage(msg);
        }

        scrollToBottom();
    }

    function appendChatMessage(msg) {
        const isOwn = msg.sender === currentUser;
        const avClass = avatarClass(msg.sender);
        const initial = msg.sender.charAt(0).toUpperCase();

        const row = document.createElement('div');
        row.className = `message-row${isOwn ? ' own' : ''}`;
        row.innerHTML = `
            <div class="message-avatar ${avClass}">${initial}</div>
            <div class="message-body">
                <div class="message-meta">
                    <span class="message-sender">${escHtml(msg.sender)}</span>
                    <span class="message-time">${formatTime(msg.timestamp)}</span>
                </div>
                <div class="message-content">${escHtml(msg.content)}</div>
            </div>
        `;
        UI.messages.appendChild(row);
    }

    function appendSystemMessage(msg) {
        const typeClass = msg.type === 'JOIN' ? 'join'
                         : msg.type === 'LEAVE' ? 'leave' : '';

        const row = document.createElement('div');
        row.className = 'message-row system';
        row.innerHTML = `<span class="system-msg ${typeClass}">${escHtml(msg.content)}</span>`;
        UI.messages.appendChild(row);
    }

    function renderHistory(messages) {
        if (!messages || messages.length === 0) return;
        removeEmptyState();

        const sep = document.createElement('div');
        sep.className = 'history-sep';
        sep.textContent = `─── last ${messages.length} messages ───`;
        UI.messages.appendChild(sep);

        messages.forEach(msg => {
            if (msg.type === 'JOIN' || msg.type === 'LEAVE' || msg.type === 'SYSTEM') {
                appendSystemMessage(msg);
            } else {
                appendChatMessage(msg);
            }
        });

        scrollToBottom();
    }

    function clearMessages() {
        UI.messages.innerHTML = '';
        UI.messages.appendChild(Object.assign(document.createElement('div'), {
            id: 'empty-state',
            className: 'empty-state',
            innerHTML: '<div class="empty-icon">✦</div><p>Loading messages…</p>'
        }));
        typingUsers = {};
        updateTypingUI();
    }

    function removeEmptyState() {
        const es = UI.messages.querySelector('.empty-state');
        if (es) es.remove();
    }

    // ── Render Users List ──────────────────────────────────────────────────────
    function renderUsersList(users) {
        UI.usersList.innerHTML = '';
        users.forEach(username => {
            const avClass = avatarClass(username);
            const isMe = username === currentUser;
            const li = document.createElement('li');
            li.className = `user-item${isMe ? ' is-me' : ''}`;
            li.innerHTML = `
                <div class="user-item-avatar ${avClass}">${username.charAt(0).toUpperCase()}</div>
                <span class="user-item-name">${escHtml(username)}</span>
            `;
            UI.usersList.appendChild(li);
        });
    }

    // ── UI Helpers ─────────────────────────────────────────────────────────────
    function showApp() {
        UI.loginScreen.classList.remove('active');
        UI.appScreen.classList.add('active');
        UI.sidebarUsername.textContent = currentUser;
        UI.userAvatar.textContent = currentUser.charAt(0).toUpperCase();
        UI.userAvatar.className = `user-avatar ${avatarClass(currentUser)}`;
        UI.messageInput.focus();
    }

    function scrollToBottom() {
        UI.messages.scrollTop = UI.messages.scrollHeight;
    }

    function updateCharCount() {
        const remaining = 500 - UI.messageInput.value.length;
        UI.charCount.textContent = remaining;
        UI.charCount.className = 'char-count' +
            (remaining < 50 ? ' danger' : remaining < 100 ? ' low' : '');
    }

    function escHtml(str) {
        return String(str)
            .replace(/&amp;/g, '&')  // undo server-side escape first
            .replace(/&lt;/g, '<')
            .replace(/&gt;/g, '>')
            .replace(/&quot;/g, '"')
            // Re-escape for DOM
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    // ── Event Listeners ────────────────────────────────────────────────────────
    UI.joinBtn.addEventListener('click', () => {
        const username = UI.usernameInput.value.trim();
        if (!username) {
            UI.usernameInput.focus();
            UI.usernameInput.style.borderColor = 'var(--danger)';
            setTimeout(() => UI.usernameInput.style.borderColor = '', 1000);
            return;
        }
        if (username.length < 2) {
            alert('Username must be at least 2 characters.');
            return;
        }
        connect(username);
    });

    UI.usernameInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') UI.joinBtn.click();
    });

    UI.sendBtn.addEventListener('click', sendMessage);

    UI.messageInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });

    UI.messageInput.addEventListener('input', () => {
        updateCharCount();
        if (UI.messageInput.value.length > 0) {
            startTyping();
        } else {
            stopTyping();
        }
    });

    // Focus username input on load
    UI.usernameInput.focus();

    // Load rooms list in login screen bg for warm-up (optional, skipped)
})();
