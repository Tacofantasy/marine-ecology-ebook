<script setup lang="ts">
import { CompassOutlined, HomeOutlined } from '@ant-design/icons-vue'
</script>

<template>
  <main class="page-shell not-found-page">
    <div class="not-found-visual" aria-hidden="true"><span>4</span><CompassOutlined /><span>4</span></div>
    <p class="section-kicker">LOST AT SEA</p>
    <h1>页面不存在</h1>
    <p>这个地址像一条消失的航线。你可以返回书库，继续探索海洋知识。</p>
    <RouterLink class="primary-button" to="/"><HomeOutlined aria-hidden="true" />返回书库</RouterLink>
  </main>
</template>
