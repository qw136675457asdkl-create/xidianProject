<template>
  <div ref="pageRef" class="app-container iframe-page">
    <div class="iframe-container" :style="{ height: iframeHeight }">
      <iframe
        :src="iframeUrl"
        class="iframe-content"
        frameborder="0"
        allowfullscreen
      />
    </div>
  </div>
</template>

<script setup>
import { nextTick, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const pageRef = ref(null)
const iframeUrl = ref('')
const iframeHeight = ref('calc(100vh - 148px)')

const PAGE_BOTTOM_GAP = 20
const DESKTOP_MIN_IFRAME_HEIGHT = 560
const MOBILE_MIN_IFRAME_HEIGHT = 420

const initUrl = () => {
  if (route.query && route.query.url) {
    iframeUrl.value = route.query.url
    return
  }

  iframeUrl.value = ''
  console.error('没有找到 url 参数')
}

const updateIframeHeight = () => {
  const page = pageRef.value
  if (!page || typeof window === 'undefined') {
    return
  }

  const rect = page.getBoundingClientRect()
  const minHeight = window.innerWidth <= 768 ? MOBILE_MIN_IFRAME_HEIGHT : DESKTOP_MIN_IFRAME_HEIGHT
  const availableHeight = window.innerHeight - rect.top - PAGE_BOTTOM_GAP

  iframeHeight.value = `${Math.max(Math.round(availableHeight), minHeight)}px`
}

const syncIframeLayout = () => {
  nextTick(() => {
    window.requestAnimationFrame(updateIframeHeight)
  })
}

initUrl()

onMounted(() => {
  syncIframeLayout()
  window.addEventListener('resize', syncIframeLayout)
})

onActivated(() => {
  syncIframeLayout()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncIframeLayout)
})

watch(
  () => route.query.url,
  (newUrl) => {
    iframeUrl.value = newUrl ? String(newUrl) : ''
    syncIframeLayout()
  }
)
</script>

<style scoped>
.iframe-page {
  min-height: 0;
  padding: 0;
}

.iframe-container {
  width: 100%;
  min-height: 420px;
  overflow: hidden;
  border: 1px solid rgba(226, 232, 240, 0.92);
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.06);
}

.iframe-content {
  width: 100%;
  height: 100%;
  border: none;
  display: block;
  background: #fff;
}

@media (max-width: 768px) {
  .iframe-container {
    min-height: 360px;
    border-radius: 12px;
  }
}
</style>
