<script setup lang="ts">
import { computed, h, onMounted, ref, type Component } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import {
  NButton,
  NDrawer,
  NDrawerContent,
  NDropdown,
  NIcon,
  NLayout,
  NLayoutContent,
  NLayoutHeader,
  NMenu,
  type MenuOption,
} from 'naive-ui'
import {
  BarChartOutline,
  BookOutline,
  CloudUploadOutline,
  FolderOpenOutline,
  HelpCircleOutline,
  LibraryOutline,
  LogOutOutline,
  MenuOutline,
  MoonOutline,
  PersonCircleOutline,
  SunnyOutline,
  TimeOutline,
} from '@vicons/ionicons5'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'

const auth = useAuthStore()
const theme = useThemeStore()
const route = useRoute()
const router = useRouter()
const drawer = ref(false)

const icon = (c: Component) => () => h(NIcon, null, { default: () => h(c) })
const link = (to: string, label: string) => () => h(RouterLink, { to }, { default: () => label })

const menuOptions = computed<MenuOption[]>(() => {
  const items: MenuOption[] = [
    { key: '/', label: link('/', 'Fanlar'), icon: icon(BookOutline) },
    { key: '/history', label: link('/history', 'Tarix'), icon: icon(TimeOutline) },
    { key: '/stats', label: link('/stats', 'Statistika'), icon: icon(BarChartOutline) },
  ]
  if (auth.isAdmin) {
    items.push({
      key: 'admin',
      label: 'Admin',
      icon: icon(LibraryOutline),
      children: [
        { key: '/admin/subjects', label: link('/admin/subjects', 'Fanlar'), icon: icon(LibraryOutline) },
        { key: '/admin/topics', label: link('/admin/topics', 'Mavzular'), icon: icon(FolderOpenOutline) },
        { key: '/admin/questions', label: link('/admin/questions', 'Savollar'), icon: icon(HelpCircleOutline) },
        { key: '/admin/import', label: link('/admin/import', 'Import'), icon: icon(CloudUploadOutline) },
      ],
    })
  }
  return items
})

const activeKey = computed(() => {
  const p = route.path
  if (p.startsWith('/admin')) return p
  if (p.startsWith('/history')) return '/history'
  if (p.startsWith('/stats')) return '/stats'
  return '/'
})

const userOptions = [{ label: 'Chiqish', key: 'logout', icon: icon(LogOutOutline) }]

function onUserSelect(key: string) {
  if (key === 'logout') {
    auth.logout()
    router.push('/login')
  }
}

onMounted(() => {
  auth.refresh().catch(() => {})
})
</script>

<template>
  <NLayout style="min-height: 100vh">
    <NLayoutHeader bordered class="header">
      <div class="header-inner">
        <NButton quaternary circle class="burger" aria-label="Menyu" @click="drawer = true">
          <template #icon><NIcon :component="MenuOutline" /></template>
        </NButton>
        <RouterLink to="/" class="brand">
          <img src="/favicon.svg" alt="" width="26" height="26" />
          <span>Test tizimi</span>
        </RouterLink>
        <div class="desktop-menu">
          <NMenu mode="horizontal" :value="activeKey" :options="menuOptions" responsive />
        </div>
        <div class="actions">
          <NButton quaternary circle :aria-label="theme.dark ? 'Yorug\' rejim' : 'Qorong\'i rejim'" @click="theme.toggle">
            <template #icon><NIcon :component="theme.dark ? SunnyOutline : MoonOutline" /></template>
          </NButton>
          <NDropdown :options="userOptions" trigger="click" @select="onUserSelect">
            <NButton quaternary>
              <template #icon><NIcon :component="PersonCircleOutline" /></template>
              <span class="user-name">{{ auth.user?.fullName ?? auth.user?.username }}</span>
            </NButton>
          </NDropdown>
        </div>
      </div>
    </NLayoutHeader>

    <NDrawer v-model:show="drawer" placement="left" :width="260">
      <NDrawerContent title="Menyu" body-content-style="padding: 0">
        <NMenu :value="activeKey" :options="menuOptions" default-expand-all @update:value="drawer = false" />
      </NDrawerContent>
    </NDrawer>

    <NLayoutContent style="min-height: calc(100vh - 61px); background: var(--page-bg)">
      <RouterView :key="route.fullPath" />
    </NLayoutContent>
  </NLayout>
</template>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 10;
}
.header-inner {
  max-width: 1100px;
  margin: 0 auto;
  padding: 0 16px;
  height: 60px;
  display: flex;
  align-items: center;
  gap: 16px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 650;
  font-size: 17px;
  color: inherit;
  text-decoration: none;
  white-space: nowrap;
}
.desktop-menu {
  flex: 1;
  min-width: 0;
}
.actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}
.burger {
  display: none;
}
@media (max-width: 760px) {
  .desktop-menu {
    display: none;
  }
  .burger {
    display: inline-flex;
  }
  .user-name {
    display: none;
  }
}
</style>
