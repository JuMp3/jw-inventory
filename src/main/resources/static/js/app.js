(function () {
    'use strict';

    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

    async function post(url, params) {
        const headers = {'Content-Type': 'application/x-www-form-urlencoded', 'Accept': 'application/json'};
        if (csrfToken && csrfHeader) {
            headers[csrfHeader] = csrfToken;
        }
        const response = await fetch(url, {method: 'POST', headers, body: new URLSearchParams(params)});
        const data = await response.json().catch(() => ({}));
        if (!response.ok) {
            throw new Error(data.error || (response.status === 403
                ? 'Sessione scaduta o permessi insufficienti: ricarica la pagina'
                : 'Operazione non riuscita'));
        }
        return data;
    }

    function showToast(message, type) {
        const container = document.getElementById('app-toast');
        if (!container) return;
        const alert = document.createElement('div');
        alert.className = 'alert shadow alert-' + (type || 'danger');
        alert.textContent = message;
        container.replaceChildren(alert);
        setTimeout(() => alert.remove(), 4000);
    }

    function flash(element, ok) {
        element.classList.remove('flash-ok', 'flash-error');
        void element.offsetWidth; // restart the animation
        element.classList.add(ok ? 'flash-ok' : 'flash-error');
    }

    // ---------- confirm before submitting destructive forms ----------
    document.querySelectorAll('form[data-confirm]').forEach(form => {
        form.addEventListener('submit', event => {
            if (!window.confirm(form.dataset.confirm)) event.preventDefault();
        });
    });

    document.querySelectorAll('select[data-autosubmit]').forEach(select => {
        select.addEventListener('change', () => select.form.submit());
    });

    // ---------- quantity widget (+1 / -1 / exact) ----------
    document.querySelectorAll('[data-qty-widget]').forEach(widget => {
        const input = widget.querySelector('.qty-input');
        const row = widget.closest('.list-group-item') || widget;
        const buttons = widget.querySelectorAll('button');

        async function send(params) {
            buttons.forEach(b => b.disabled = true);
            try {
                const result = await post(widget.dataset.url, params);
                input.value = result.quantity;
                input.dataset.last = result.quantity;
                flash(row, true);
            } catch (error) {
                input.value = input.dataset.last;
                flash(row, false);
                showToast(error.message);
            } finally {
                buttons.forEach(b => b.disabled = false);
            }
        }

        widget.querySelectorAll('[data-action]').forEach(button =>
            button.addEventListener('click', () => send({action: button.dataset.action})));
        input.addEventListener('change', () => {
            if (input.value === input.dataset.last) return;
            send({action: 'set', value: input.value});
        });
        input.addEventListener('keydown', event => {
            if (event.key === 'Enter') {
                event.preventDefault();
                input.blur();
            }
        });
    });

    // ---------- inventory count ----------
    function renderDiff(row) {
        const badge = row.querySelector('[data-diff-badge]');
        if (!badge) return;
        const diff = row.dataset.diff;
        badge.classList.remove('text-bg-success', 'text-bg-danger');
        if (diff === undefined || diff === '') {
            badge.textContent = '';
            badge.hidden = true;
            return;
        }
        const value = Number(diff);
        badge.hidden = false;
        badge.textContent = value === 0 ? 'OK' : (value > 0 ? '+' + value : String(value));
        badge.classList.add(value === 0 ? 'text-bg-success' : 'text-bg-danger');
    }

    function updateStats(list) {
        const rows = [...list.querySelectorAll('[data-count-row]')];
        const counted = rows.filter(r => (r.dataset.counted ?? '') !== '').length;
        const differences = rows.filter(r => (r.dataset.diff ?? '') !== '' && r.dataset.diff !== '0').length;
        document.querySelectorAll('[data-stat="counted"]').forEach(el => el.textContent = counted);
        document.querySelectorAll('[data-stat="differences"]').forEach(el => el.textContent = differences);
    }

    document.querySelectorAll('[data-count-row]').forEach(row => {
        renderDiff(row);
        const list = row.parentElement;
        const input = row.querySelector('.count-input');
        const url = list.dataset.countUrl;
        if (!input || !url) return;

        async function save() {
            try {
                const result = await post(url, {publicationId: row.dataset.publicationId, counted: input.value.trim()});
                row.dataset.counted = result.counted ?? '';
                row.dataset.diff = result.difference ?? '';
                row.dataset.expected = result.expected;
                row.querySelector('[data-expected-label]').textContent = result.expected;
                renderDiff(row);
                updateStats(list);
                applyFilters(list);
                flash(row, true);
            } catch (error) {
                input.value = row.dataset.counted ?? '';
                flash(row, false);
                showToast(error.message);
            }
        }

        input.addEventListener('change', save);
        input.addEventListener('keydown', event => {
            if (event.key === 'Enter') {
                event.preventDefault();
                input.blur();
            }
        });
        row.querySelector('[data-match]')?.addEventListener('click', () => {
            input.value = row.dataset.expected;
            save();
        });
    });

    // ---------- client-side filters (search text + optional mode) ----------
    function applyFilters(list) {
        const selector = '#' + list.id;
        const text = (document.querySelector('[data-filter-input="' + selector + '"]')?.value || '')
            .trim().toLowerCase();
        const mode = document.querySelector('[data-filter-mode="' + selector + '"]')?.value || 'all';
        let visible = 0;
        list.querySelectorAll('[data-filter-item]').forEach(item => {
            const counted = item.dataset.counted ?? '';
            const diff = item.dataset.diff ?? '';
            const matchesText = !text || (item.dataset.filterText || '').toLowerCase().includes(text);
            const matchesMode = mode === 'all'
                || (mode === 'todo' && counted === '')
                || (mode === 'diff' && diff !== '' && diff !== '0');
            item.hidden = !(matchesText && matchesMode);
            if (!item.hidden) visible++;
        });
        const empty = document.querySelector('[data-filter-empty="' + selector + '"]');
        if (empty) empty.hidden = visible > 0;
    }

    document.querySelectorAll('[data-filter-input], [data-filter-mode]').forEach(control => {
        const list = document.querySelector(control.dataset.filterInput || control.dataset.filterMode);
        if (!list) return;
        control.addEventListener(control.tagName === 'SELECT' ? 'change' : 'input', () => applyFilters(list));
    });

    // ---------- notification form: show only the fields for the chosen frequency ----------
    document.querySelectorAll('[data-schedule-type]').forEach(select => {
        const form = select.closest('form');
        const update = () => form.querySelectorAll('[data-show-for]').forEach(el =>
            el.hidden = !el.dataset.showFor.split(' ').includes(select.value));
        select.addEventListener('change', update);
        update();
    });

    // ---------- image upload: size check + preview ----------
    document.querySelectorAll('input[type="file"][data-max-bytes]').forEach(input => {
        input.addEventListener('change', () => {
            const file = input.files[0];
            if (!file) return;
            if (file.size > Number(input.dataset.maxBytes)) {
                showToast('Immagine troppo grande (massimo 2 MB)');
                input.value = '';
                return;
            }
            const preview = document.querySelector(input.dataset.preview);
            if (preview) {
                preview.src = URL.createObjectURL(file);
                preview.hidden = false;
                preview.parentElement.querySelector('[data-preview-placeholder]')?.setAttribute('hidden', '');
            }
        });
    });
})();
