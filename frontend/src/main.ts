import { createApp } from 'vue'
import { Alert, Button, Card, Form, Input, Layout, Popconfirm, Spin } from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import './style.css'
import App from './App.vue'
import router from './router'

createApp(App)
  .use(Alert)
  .use(Button)
  .use(Card)
  .use(Form)
  .use(Input)
  .use(Layout)
  .use(Popconfirm)
  .use(Spin)
  .use(router)
  .mount('#app')
