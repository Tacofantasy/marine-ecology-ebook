import { createApp } from 'vue'
import { Alert, Button, Card, Form, Input, Layout, Modal, Pagination, Popconfirm, Select, Space, Spin, Table, Tag, Upload } from 'ant-design-vue'
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
  .use(Pagination)
  .use(Popconfirm)
  .use(Select)
  .use(Space)
  .use(Spin)
  .use(Table)
  .use(Tag)
  .use(Upload)
  .use(router)
  .mount('#app')
