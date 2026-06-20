import {createRouter, createWebHistory } from 'vue-router';

const routes = [
    {
        path: '/',
        name: 'Home',
        // component: () => import('../views/Home.vue')
        component: () => import('../components/Board-Flex.vue')
    },
    {
        path: '/cards',
        name: 'Search',
        // component: () => import('../views/Home.vue')
        component: () => import('../views/SearchView.vue')
    },
    {
        path: '/card/:id',
        name: 'CardDetail',
        component: () => import('../views/CardDetail.vue'),
        props: true // 讓 id 直接變成元件的 props
    },
    {
        path: '/card/new',
        name: 'CardCreate',
        component: () => import('../views/CardDetail.vue'),
        props: true // 讓 id 直接變成元件的 props
    },
    {
        path: '/login',
        name: 'Login',
        component: () => import('../views/Login.vue')
    },
    {
        path: '/register',
        name: 'Register',
        component: () => import('../views/Register.vue')
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

export default router;