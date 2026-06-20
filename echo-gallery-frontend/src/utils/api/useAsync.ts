import { ref } from "vue";

export function useAsync<T, Args extends any[]>(
    asyncFn: (...args: Args) => Promise<T>
){
    const data = ref<T | null>(null);
    const loading = ref(false);
    const error = ref<any>(null);

    const execute = async (...args: Args) => {
        loading.value = true;
        error.value = null;
        try {
            const res = await asyncFn(...args);
            data.value = res;
            return res;
        } catch (err) {
            error.value = err;
            throw err;
        } finally {
            loading.value = false;
        }
    }

    return {
        data,
        loading,
        error,
        execute
    }

}


// <script setup>
// import { cardApi } from '../api/card'
// import { useAsync } from '../composables/useAsync'

// // 💡 傳入 API 函式，直接解構出需要的狀態與執行函式
// const { loading, execute: fetchCards } = useAsync(cardApi.getCards)

// const loadMore = () => {
//   // 呼叫執行，內部會自動幫你轉 loading.value = true / false
//   fetchCards({ page: 1 }).then(res => {
//     cardList.value.push(...res)
//   })
// }
// </script>

// <template>
//   <p v-if="loading">載入中...</p>
// </template>