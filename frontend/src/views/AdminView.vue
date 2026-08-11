<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, ensureCsrf, errorMessage } from '../api/client'
import { useUiStore } from '../stores/ui'

interface UserRow { id: number; username: string; role: string; disabled: boolean; totpEnabled: boolean; mustChangePassword: boolean; createdAt: string }
interface InviteRow { id: number; createdBy: string; usedBy: string | null; expiresAt: string; usedAt: string | null; createdAt: string }
interface AuditRow { id: number; username: string | null; eventType: string; status: string; ipAddress: string | null; targetType: string | null; targetId: string | null; details: string | null; createdAt: string }

const ui = useUiStore()
const users = ref<UserRow[]>([])
const invites = ref<InviteRow[]>([])
const audits = ref<AuditRow[]>([])
const newInvite = ref<{ code: string; expiresAt: string } | null>(null)
const pending = ref(false)
const error = ref('')

function date(value: string | null) {
  return value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) : '—'
}

async function load() {
  error.value = ''
  try {
    const [u, i, a] = await Promise.all([
      api.get<UserRow[]>('/admin/users'), api.get<InviteRow[]>('/admin/invites'), api.get<AuditRow[]>('/admin/audits')
    ])
    users.value = u.data
    invites.value = i.data
    audits.value = a.data
  } catch (e) {
    error.value = errorMessage(e)
  }
}

async function createInvite() {
  pending.value = true
  try {
    await ensureCsrf()
    const { data } = await api.post<{ code: string; expiresAt: string }>('/admin/invites', { expiresInDays: 7 })
    newInvite.value = data
    await load()
  } catch (e) { error.value = errorMessage(e) } finally { pending.value = false }
}

async function setDisabled(user: UserRow) {
  if (!window.confirm(`${user.disabled ? '启用' : '禁用'}账号“${user.username}”？`)) return
  try {
    await ensureCsrf()
    await api.post(`/admin/users/${user.id}/state`, { disabled: !user.disabled })
    ui.toast('success', '账号状态已更新')
    await load()
  } catch (e) { error.value = errorMessage(e) }
}

async function resetPassword(user: UserRow) {
  const value = window.prompt(`为“${user.username}”设置临时密码（至少 12 位）`)
  if (!value) return
  try {
    await ensureCsrf()
    await api.post(`/admin/users/${user.id}/reset-password`, { password: value })
    ui.toast('success', '临时密码已设置，该用户下次登录必须修改密码')
    await load()
  } catch (e) { error.value = errorMessage(e) }
}

async function copyInvite() {
  if (!newInvite.value) return
  await navigator.clipboard.writeText(newInvite.value.code)
  ui.toast('success', '邀请码已复制')
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-heading">
      <div><p class="eyebrow">INSTANCE CONTROL</p><h1>实例管理</h1><p>管理成员、一次性邀请码和最近的安全审计事件。</p></div>
      <button class="btn btn-primary" type="button" :disabled="pending" @click="createInvite">生成 7 天邀请码</button>
    </div>
    <p v-if="error" class="error-banner" role="alert"><span>{{ error }}</span><button class="btn btn-sm" @click="load">重试</button></p>
    <div v-if="newInvite" class="invite-callout" role="status">
      <div><strong>新邀请码仅显示在这里</strong><code>{{ newInvite.code }}</code><span>有效期至 {{ date(newInvite.expiresAt) }}，使用一次后失效。</span></div>
      <button class="btn" type="button" @click="copyInvite">复制邀请码</button>
    </div>

    <section>
      <div class="section-title"><h2>成员</h2><span>{{ users.length }} 个账号</span></div>
      <div class="table-wrap"><table class="data"><thead><tr><th>账号</th><th>角色</th><th>双重验证</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead>
        <tbody><tr v-for="user in users" :key="user.id"><td><strong>{{ user.username }}</strong></td><td>{{ user.role === 'ADMIN' ? '管理员' : '成员' }}</td><td><span :class="user.totpEnabled ? 'badge badge-success' : 'badge badge-muted'">{{ user.totpEnabled ? '已启用' : '未启用' }}</span></td><td><span :class="user.disabled ? 'badge badge-danger' : 'badge badge-success'">{{ user.disabled ? '已禁用' : user.mustChangePassword ? '待改密码' : '正常' }}</span></td><td>{{ date(user.createdAt) }}</td><td class="actions"><button class="btn btn-sm" type="button" @click="resetPassword(user)">重置密码</button><button class="btn btn-sm" type="button" @click="setDisabled(user)">{{ user.disabled ? '启用' : '禁用' }}</button></td></tr></tbody>
      </table></div>
    </section>

    <section>
      <div class="section-title"><h2>邀请码</h2><span>最近 {{ invites.length }} 条</span></div>
      <div class="table-wrap"><table class="data"><thead><tr><th>创建者</th><th>创建时间</th><th>有效期</th><th>使用者</th><th>状态</th></tr></thead>
        <tbody><tr v-for="invite in invites" :key="invite.id"><td>{{ invite.createdBy }}</td><td>{{ date(invite.createdAt) }}</td><td>{{ date(invite.expiresAt) }}</td><td>{{ invite.usedBy || '—' }}</td><td><span :class="invite.usedAt ? 'badge badge-muted' : 'badge badge-accent'">{{ invite.usedAt ? '已使用' : '未使用' }}</span></td></tr></tbody>
      </table></div>
    </section>

    <section>
      <div class="section-title"><h2>安全审计</h2><span>最近 {{ audits.length }} 条</span></div>
      <div class="table-wrap"><table class="data"><thead><tr><th>时间</th><th>账号</th><th>事件</th><th>结果</th><th>IP</th><th>详情</th></tr></thead>
        <tbody><tr v-for="audit in audits" :key="audit.id"><td>{{ date(audit.createdAt) }}</td><td>{{ audit.username || '—' }}</td><td><code>{{ audit.eventType }}</code></td><td><span :class="audit.status === 'SUCCESS' ? 'badge badge-success' : 'badge badge-danger'">{{ audit.status }}</span></td><td class="mono">{{ audit.ipAddress || '—' }}</td><td>{{ audit.details || [audit.targetType, audit.targetId].filter(Boolean).join(' #') || '—' }}</td></tr></tbody>
      </table></div>
    </section>
  </div>
</template>

<style scoped>
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 18px; }
.page-heading h1 { margin: 2px 0 4px; font-size: 25px; }
.page-heading p:not(.eyebrow) { margin: 0; color: var(--text-secondary); }
.eyebrow { margin: 0; color: var(--accent); font: 700 10px/1 var(--font-mono); letter-spacing: .14em; }
.invite-callout { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 15px 17px; margin-bottom: 22px; border: 1px solid #f0c36a; border-radius: var(--radius); background: #fff9e9; color: #563b00; }
.invite-callout > div { display: grid; grid-template-columns: auto 1fr; align-items: center; gap: 3px 14px; }
.invite-callout code { font-size: 16px; letter-spacing: .06em; }
.invite-callout span { grid-column: 1 / -1; font-size: 11px; }
section { margin-bottom: 24px; }
.section-title { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 8px; }
.section-title h2 { margin: 0; }
.section-title span { color: var(--text-muted); font-size: 11px; }
.actions { display: flex; gap: 6px; }
@media (max-width: 620px) { .page-heading, .invite-callout { align-items: stretch; flex-direction: column; } .invite-callout > div { grid-template-columns: 1fr; } .invite-callout span { grid-column: auto; } }
</style>
