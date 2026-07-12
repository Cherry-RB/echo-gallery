import {createRouter, createWebHistory } from 'vue-router';
import { jwtDecode } from 'jwt-decode';

interface JwtPayload {
  exp: number; // 過期時間 (unix timestamp, 秒)
  [key: string]: any;
}

// 檢查 token 是否存在且未過期
function isTokenValid(token: string | null): boolean {
  if (!token) return false;
  try {
    const { exp } = jwtDecode<JwtPayload>(token);
    return exp * 1000 > Date.now();
  } catch {
    // decode 失敗代表格式不對，視為無效
    return false;
  }
}

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
      path: 'card/new', // 新建卡片
      name: 'CardCreate',
      component: () => import('../views/CardDetail.vue'),
      props: true // 讓 id 直接變成元件的 props
    },
    {
      path: 'card/:id', // 卡片詳情/更新卡片
      name: 'CardDetail',
      component: () => import('../views/CardDetail.vue'),
      props: true
    },
    {
      path: 'board/today', // 今日看板
      name: 'TodayBoard',
      component: () => import('../views/boards/TodayBoard.vue')
    },
    {
      path: 'board/all', // 全部卡片
      name: 'AllCardsBoard',
      component: () => import('../views/boards/AllCardsBoard.vue')
    },
    {
      path: 'board/hot', // 熱門看板
      name: 'HotBoard',
      component: () => import('../views/boards/HotBoard.vue')
    },
    {
      path: 'board/random', // 隨機看板
      name: 'RandomBoard',
      component: () => import('../views/boards/RandomBoard.vue')
    },
    {
      path: 'board/archived', // 封存看板
      name: 'ArchivedBoard',
      component: () => import('../views/boards/ArchivedBoard.vue')
    },
    {
      path: 'board/snoozed', // 稍後再看看板
      name: 'SnoozedBoard',
      component: () => import('../views/boards/SnoozedBoard.vue')
    },
    {
      path: 'search', // 查詢看板
      name: 'Search',
      component: () => import('../views/SearchView.vue')
    },
    {
      path: '', // 預設導向 今日看板
      redirect: 'board/today'
    }
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
    scrollBehavior(_to, _from, savedPosition) {
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
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token');
  const tokenValid = isTokenValid(token);

  // token 存在但已過期 -> 順手清掉，避免殘留髒資料
  if (token && !tokenValid) {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
  }

  // Vue Router 4 會自動將父路由的 meta 合併到子路由的 to.meta 中
  const requiresAuth = to.meta.requiresAuth;
  const isGuestOnly = to.meta.guestOnly;

  // 1. 該頁面需要登入，但使用者沒有 token -> 踢回登入頁
  if (requiresAuth && !tokenValid) {
    next({ name: 'Login' });
  }
  // 2. 該頁面僅限訪客（如登入/註冊），但使用者已有 token -> 導回首頁
  else if (isGuestOnly && tokenValid) {
    next({ name: 'TodayBoard' });
  }
  // 3. 其餘放行
  else {
    next();
  }
});

export default router;
