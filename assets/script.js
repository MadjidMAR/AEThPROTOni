(() => {
    const html = document.documentElement;
    const themeToggle = document.getElementById('theme-toggle');
    const mobileThemeToggle = document.getElementById('mobile-theme-toggle');
    const mobileMenuBtn = document.getElementById('mobile-menu-btn');
    const mobileMenu = document.getElementById('mobile-menu');
    const mobileOverlay = document.getElementById('mobile-overlay');
    const mobilePanel = document.getElementById('mobile-panel');
    const navbar = document.getElementById('navbar');
    const hamburgerLines = mobileMenuBtn.querySelectorAll('.hamburger-line');
    let menuOpen = false;
    let menuAnimating = false;

    function getPreferredTheme() {
        const stored = localStorage.getItem('theme');
        if (stored) return stored;
        return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }

    function applyTheme(theme) {
        if (theme === 'dark') {
            html.classList.add('dark');
            html.classList.remove('light');
        } else {
            html.classList.add('light');
            html.classList.remove('dark');
        }
        localStorage.setItem('theme', theme);
    }

    applyTheme(getPreferredTheme());

    function toggleTheme() {
        const current = html.classList.contains('dark') ? 'dark' : 'light';
        applyTheme(current === 'dark' ? 'light' : 'dark');
    }

    themeToggle.addEventListener('click', toggleTheme);
    mobileThemeToggle.addEventListener('click', toggleTheme);

    function openMenu() {
        if (menuAnimating) return;
        menuAnimating = true;
        menuOpen = true;

        mobileOverlay.classList.remove('bg-black/0', 'dark:bg-black/0');
        mobileOverlay.classList.add('bg-black/40', 'dark:bg-black/60');
        mobilePanel.classList.remove('translate-x-full');
        mobilePanel.classList.add('translate-x-0');
        document.body.style.overflow = 'hidden';

        hamburgerLines[0].style.transform = 'rotate(45deg) translate(0, 0)';
        hamburgerLines[1].style.transform = 'scaleX(0)';
        hamburgerLines[2].style.transform = 'rotate(-45deg) translate(0, 0)';

        setTimeout(() => { menuAnimating = false; }, 300);
    }

    function closeMenu() {
        if (menuAnimating) return;
        menuAnimating = true;
        menuOpen = false;

        mobileOverlay.classList.add('bg-black/0', 'dark:bg-black/0');
        mobileOverlay.classList.remove('bg-black/40', 'dark:bg-black/60');
        mobilePanel.classList.add('translate-x-full');
        mobilePanel.classList.remove('translate-x-0');
        document.body.style.overflow = '';

        hamburgerLines[0].style.transform = '';
        hamburgerLines[1].style.transform = '';
        hamburgerLines[2].style.transform = '';

        setTimeout(() => { menuAnimating = false; }, 300);
    }

    mobileMenuBtn.addEventListener('click', () => {
        if (menuOpen) {
            closeMenu();
        } else {
            openMenu();
        }
    });

    mobileOverlay.addEventListener('click', closeMenu);

    mobileMenu.querySelectorAll('[data-tab]').forEach(btn => {
        btn.addEventListener('click', () => {
            closeMenu();
            switchTab(btn.dataset.tab);
        });
    });

    mobileMenu.querySelectorAll('a[href]').forEach(link => {
        link.addEventListener('click', closeMenu);
    });

    function switchTab(tabId) {
        document.querySelectorAll('.tab-page').forEach(page => {
            page.classList.add('hidden');
            page.classList.remove('active');
        });

        const target = document.getElementById('page-' + tabId);
        if (target) {
            target.classList.remove('hidden');
            target.classList.add('active');
        }

        document.querySelectorAll('.nav-tab').forEach(btn => {
            btn.classList.remove('bg-gray-100', 'dark:bg-gray-800', 'text-gray-900', 'dark:text-white');
            btn.classList.add('text-gray-600', 'dark:text-gray-400');
        });
        document.querySelectorAll(`.nav-tab[data-tab="${tabId}"]`).forEach(btn => {
            btn.classList.add('bg-gray-100', 'dark:bg-gray-800', 'text-gray-900', 'dark:text-white');
            btn.classList.remove('text-gray-600', 'dark:text-gray-400');
        });

        window.scrollTo({ top: 0, behavior: 'smooth' });
        localStorage.setItem('activeTab', tabId);
    }

    document.querySelectorAll('[data-tab]').forEach(btn => {
        btn.addEventListener('click', () => {
            switchTab(btn.dataset.tab);
        });
    });

    const savedTab = localStorage.getItem('activeTab');
    if (savedTab && document.getElementById('page-' + savedTab)) {
        switchTab(savedTab);
    } else {
        switchTab('home');
    }

    let lastScroll = 0;
    window.addEventListener('scroll', () => {
        const scrollY = window.scrollY;
        if (scrollY > 10) {
            navbar.classList.add('navbar-scrolled');
        } else {
            navbar.classList.remove('navbar-scrolled');
        }
        lastScroll = scrollY;
    }, { passive: true });

    document.querySelectorAll('.config-toggle').forEach(toggle => {
        toggle.addEventListener('click', () => {
            const section = toggle.closest('.config-section');
            const content = section.querySelector('.config-content');
            const icon = toggle.querySelector('svg');
            const isHidden = content.classList.contains('hidden');

            if (isHidden) {
                content.classList.remove('hidden');
                icon.classList.add('rotated');
                toggle.setAttribute('aria-expanded', 'true');
            } else {
                content.classList.add('hidden');
                icon.classList.remove('rotated');
                toggle.setAttribute('aria-expanded', 'false');
            }
        });
    });
})();