import {createRouter, createWebHistory } from 'vue-router';

const routes = [
  // 1. 不需要側邊欄的頁面（頂層路由）
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { requiresAuth: false, guestOnly: true } // 訪客限定，登入後不能來
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/Register.vue'),
    meta: { requiresAuth: false, guestOnly: true }
  },
  {
    path: '/logout',
    name: 'Logout',
    component: () => import('../components/Logout.vue'),
    meta: { requiresAuth: true }
  },
  // 2. 需要側邊欄的頁面（巢狀路由）
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    meta: { requiresAuth: true }, // 直接在父路由加上需要權限，子路由會自動繼承
    children: [
    {
      path: '/card/new', // 新建卡片
      name: 'CardCreate',
      component: () => import('../views/CardDetail.vue'),
      props: true // 讓 id 直接變成元件的 props
    },
    {
      path: '/card/:id', // 卡片詳情/更新卡片
      name: 'CardDetail',
      component: () => import('../views/CardDetail.vue'),
      props: true
    },
    {
      path: '/board/today', // 今日看板
      name: 'Home',
      component: () => import('../components/Board-Flex.vue')
    },
    {
      path: '/board/random', // 隨機看板
      name: 'Home',
      component: () => import('../components/Board-Flex.vue')
    },
    {
      path: '/board/all', // 最新看板
      name: 'Home',
      component: () => import('../components/Board-Flex.vue')
    },
    {
      path: '/board/hot', // 熱門看板
      name: 'Home',
      component: () => import('../components/Board-Flex.vue')
    },
    {
      path: '/board/archived', // 封存看板
      name: 'Home',
      component: () => import('../components/Board-Flex.vue')
    },
    {
      path: '/board/snoozed', // 稍後再看看板
      name: 'Home',
      component: () => import('../components/Board-Flex.vue')
    },
    {
      path: '/search', // 查詢看板
      name: 'Search',
      component: () => import('../views/SearchView.vue')
    },
  ]},
  // 3. 補上 404 頁面（選用，增加體驗）
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
    history: createWebHistory(),
    routes,
    // 關鍵自動回滾機制：只要改這裡就好！
    scrollBehavior(to, from, savedPosition) {
        if (savedPosition) {
          // 當使用者點擊「瀏覽器返回」或 router.back() 時，savedPosition 會自動帶出當年的坐標 { top: xxx, left: xxx }
          return savedPosition
        } else {
          // 如果是從別的地方點選連結新進來的，就自動置頂
          return { top: 0 }
        }
    }
})

// 加入路由守衛
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token');

  // Vue Router 4 會自動將父路由的 meta 合併到子路由的 to.meta 中
  const requiresAuth = to.meta.requiresAuth;
  const isGuestOnly = to.meta.guestOnly;

  // 1. 該頁面需要登入，但使用者沒有 token -> 踢回登入頁
  if (requiresAuth && !token) {
    next({ name: 'Login' });
  }
  // 2. 該頁面僅限訪客（如登入/註冊），但使用者已有 token -> 導回首頁
  else if (isGuestOnly && token) {
    next({ name: 'Home' });
  }
  // 3. 其餘放行
  else {
    next();
  }
});

export default router;
