import {createRouter, createWebHistory } from 'vue-router';

const routes = [
  // 1. 不需要側邊欄的頁面（頂層路由）
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/Register.vue')
  },
  {
    path: '/logout',
    name: 'Logout',
    component: () => import('../components/Logout.vue')
  },
  // 2. 需要側邊欄的頁面（巢狀路由）
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    children: [
    {
        path: '',
        name: 'Home',
        component: () => import('../components/Board-Flex.vue')
    },
    {
        path: '/cards',
        name: 'Search',
        component: () => import('../views/SearchView.vue')
    },
    {
        path: '/card/:id',
        name: 'CardDetail',
        component: () => import('../views/CardDetail.vue'),
        props: true
    },
    {
        path: '/card/new',
        name: 'CardCreate',
        component: () => import('../views/CardDetail.vue'),
        props: true // 讓 id 直接變成元件的 props
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

  // 如果要去非登入/註冊頁面，且沒有 token，就強制踢回登入頁
  if (to.name !== 'Login' && to.name !== 'Register' && !token) {
    next({ name: 'Login' });
  }
  // 如果已經登入，還硬要連到登入頁，直接導回首頁
  else if ((to.name === 'Login' || to.name === 'Register') && token) {
    next({ name: 'Home' });
  }
  else {
    next();
  }
});

export default router;
