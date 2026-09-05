import { test, expect, type APIRequestContext, type Page } from '@playwright/test'

const api = 'http://127.0.0.1:18080'
const password = 'password123'
let adminToken: string
let ebookId: string
let chapterId: string
let secondChapterId: string
let username: string

async function login(page: Page, account: string, secret: string) {
  await page.goto('/login')
  await page.getByLabel('登录名或邮箱').fill(account)
  await page.getByLabel('密码', { exact: true }).fill(secret)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/$/)
}

async function post(request: APIRequestContext, path: string, data?: unknown) {
  const response = await request.post(api + path, { headers: { satoken: adminToken }, data })
  expect(response.ok(), await response.text()).toBeTruthy()
  return (await response.json()).data
}

test.beforeAll(async ({ request }) => {
  username = `reader_${Date.now()}`
  const admin = await request.post(`${api}/api/auth/login`, { data: { account: 'admin', password: 'password' } })
  expect(admin.ok()).toBeTruthy()
  adminToken = (await admin.json()).data.token
  const categories = await request.get(`${api}/api/categories`)
  const categoryId = (await categories.json()).data.flatMap((item: { children: unknown[] }) => item.children)[0].id
  const book = await post(request, '/api/admin/ebooks', {
    categoryId, title: `浏览器验收 ${Date.now()}`,
    summary: '这是用于浏览器自动验收的海洋生态科普电子书，验证完整的内容管理和阅读流程。',
    sourceNote: '项目组自制测试内容，仅用于自动验收。',
  })
  ebookId = book.id
  chapterId = (await post(request, `/api/admin/ebooks/${ebookId}/chapters`, { title: '第一章 海洋生态', content: '<p>第一章的海洋生态正文。</p>' })).id
  secondChapterId = (await post(request, `/api/admin/ebooks/${ebookId}/chapters`, { title: '第二章 珊瑚保护', content: '<p>第二章的珊瑚保护正文。</p>' })).id
  const cover = await request.post(`${api}/api/admin/ebooks/cover?ebookId=${ebookId}`, {
    headers: { satoken: adminToken },
    multipart: { file: { name: 'cover.png', mimeType: 'image/png', buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5V8AAAAASUVORK5CYII=', 'base64') } },
  })
  expect(cover.ok(), await cover.text()).toBeTruthy()
  await post(request, `/api/admin/ebooks/${ebookId}/publish`)
  const registered = await request.post(`${api}/api/auth/register`, { data: { username, email: `${username}@example.com`, password } })
  expect(registered.ok()).toBeTruthy()
})

test.afterAll(async ({ request }) => {
  if (ebookId && adminToken) {
    await request.post(`${api}/api/admin/ebooks/${ebookId}/unpublish`, { headers: { satoken: adminToken } })
    await request.delete(`${api}/api/admin/ebooks/${ebookId}`, { headers: { satoken: adminToken } })
  }
})

test('读者登录、章节地址、点赞收藏与阅读身份', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await login(page, username, password)
  const readRequest = page.waitForRequest(r => r.url().endsWith(`/chapters/${secondChapterId}/read`))
  await page.goto(`/ebooks/${ebookId}/read?chapter=${secondChapterId}`)
  await expect(page.getByRole('heading', { name: '第二章 珊瑚保护', exact: true })).toBeVisible()
  expect((await readRequest).headers().satoken).toBeTruthy()
  await expect(page.getByText('内容来源说明：项目组自制测试内容，仅用于自动验收。')).toBeVisible()
  await page.getByRole('button', { name: '点赞', exact: true }).click()
  await expect(page.getByRole('button', { name: '已点赞', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '收藏', exact: true }).click()
  await expect(page.getByRole('button', { name: '已收藏', exact: true })).toBeVisible()
  await page.getByRole('button', { name: /上一章 · 第一章/ }).click()
  await expect(page).toHaveURL(new RegExp(`chapter=${chapterId}`))
  await page.reload()
  await expect(page.getByRole('heading', { name: '第一章 海洋生态', exact: true })).toBeVisible()
  await page.goto('/favorites')
  await expect(page.getByRole('link', { name: '阅读此书' })).toBeVisible()
  expect(errors).toEqual([])
})

test('图表请求失败后可重试恢复，移动端无横向溢出', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.route('**/api/stats/summary', route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 50000, message: '验收模拟服务中断' }) }))
  await page.goto('/')
  await expect(page.getByText('验收模拟服务中断')).toBeVisible()
  await page.unroute('**/api/stats/summary')
  await page.getByRole('button', { name: '重新加载', exact: true }).click()
  await expect(page.locator('.stats-chart canvas')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
})

test('过期登录可恢复登录入口，不存在的地址显示说明', async ({ page }) => {
  await page.goto('/')
  await page.evaluate(() => sessionStorage.setItem('marine-ebook-auth', JSON.stringify({ token: 'expired-token', user: { id: '1', username: 'admin', displayName: '管理员', role: 'SUPER_ADMIN', status: 1 } })))
  await page.goto('/admin/ebooks')
  await expect(page).toHaveURL(/\/login\?redirect=/)
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible()
  await page.goto('/missing-page')
  await expect(page.getByRole('heading', { name: '页面不存在' })).toBeVisible()
})

test('切章失败后重试加载目标章节', async ({ page }) => {
  await page.goto(`/ebooks/${ebookId}/read?chapter=${chapterId}`)
  await expect(page.getByRole('heading', { name: '第一章 海洋生态', exact: true })).toBeVisible()
  const target = `**/api/ebooks/${ebookId}/chapters/${secondChapterId}`
  await page.route(target, route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 50000, message: '章节暂时无法加载' }) }))
  await page.getByRole('button', { name: /下一章 · 第二章/ }).click()
  await expect(page.getByText('章节暂时无法加载')).toBeVisible()
  await page.unroute(target)
  await page.getByRole('button', { name: '重试加载章节' }).click()
  await expect(page.getByRole('heading', { name: '第二章 珊瑚保护', exact: true })).toBeVisible()
})

test('管理端搜索保持章节序号，编辑离开保护生效', async ({ page, request }) => {
  await post(request, `/api/admin/ebooks/${ebookId}/unpublish`)
  await login(page, 'admin', 'password')
  await page.goto(`/admin/ebooks/${ebookId}/chapters`)
  await page.getByPlaceholder('搜索章节标题或来源').fill('第二章')
  await expect(page.locator('.chapter-number')).toHaveText('2')
  await expect(page.getByRole('button', { name: '上移', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  await page.getByLabel('章节标题', { exact: true }).fill('修改尚未保存')
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.getByText('放弃未保存的修改？')).toBeVisible()
  await page.getByRole('button', { name: '继续编辑' }).click()
  await expect(page.getByLabel('章节标题', { exact: true })).toHaveValue('修改尚未保存')
  await page.getByRole('button', { name: '保存章节', exact: true }).click()
  await expect(page.getByText('章节已更新', { exact: true })).toBeVisible()
})
