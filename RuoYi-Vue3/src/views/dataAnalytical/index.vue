<template>
  <div class="iframe-container">
    <iframe
        :src="iframeUrl"
        class="iframe-content"
        frameborder="0"
        allowfullscreen
    />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const iframeUrl = ref('')

// 初始化从路由参数获取URL
const initUrl = () => {
  if (route.query && route.query.url) {
    iframeUrl.value = route.query.url
    console.log('加载iframe:', iframeUrl.value)
  } else {
    console.error('没有找到url参数')
  }
}

// 初始化
initUrl()

// 监听路由变化（如果通过标签页切换）
watch(() => route.query.url, (newUrl) => {
  if (newUrl) {
    iframeUrl.value = newUrl
  }
})
</script>

<style scoped>
.iframe-container {
  height: 100%;
  width: 100%;
  overflow: hidden;
  background: #f0f2f5;
}
.iframe-content {
  width: 100%;
  height: 100%;
  border: none;
  display: block;
}
</style>