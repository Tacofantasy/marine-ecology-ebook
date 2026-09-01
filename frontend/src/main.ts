import { createApp } from 'vue'
import { Alert, Button, Card, Form, Input, Layout, Modal, Popconfirm, Space, Spin, Table } from 'ant-design-vue'
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
  .use(Modal)
  .use(Popconfirm)
  .use(Space)
  .use(Spin)
  .use(Table)
  .use(router)
  .mount('#app')
