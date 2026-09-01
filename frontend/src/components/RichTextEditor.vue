<script setup>
import { watch, onBeforeUnmount } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Link from '@tiptap/extension-link'
import Placeholder from '@tiptap/extension-placeholder'

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '본문을 입력하세요. 서식·목록·링크를 사용할 수 있습니다.' },
})
const emit = defineEmits(['update:modelValue'])

const editor = useEditor({
  content: props.modelValue,
  extensions: [
    StarterKit.configure({ heading: { levels: [2, 3] } }),
    Link.configure({ openOnClick: false, autolink: true }),
    Placeholder.configure({ placeholder: props.placeholder }),
  ],
  onUpdate: ({ editor }) => {
    const html = editor.isEmpty ? '' : editor.getHTML()
    emit('update:modelValue', html)
  },
})

// 외부에서 값이 바뀌면(비동기 로드 등) 에디터에 반영
watch(
  () => props.modelValue,
  (val) => {
    if (!editor.value) return
    if (val !== (editor.value.isEmpty ? '' : editor.value.getHTML())) {
      editor.value.commands.setContent(val || '', false)
    }
  },
)

onBeforeUnmount(() => editor.value?.destroy())

function setLink() {
  const prev = editor.value.getAttributes('link').href
  const url = window.prompt('링크 URL', prev || 'https://')
  if (url === null) return
  if (url === '') {
    editor.value.chain().focus().extendMarkRange('link').unsetLink().run()
    return
  }
  editor.value.chain().focus().extendMarkRange('link').setLink({ href: url }).run()
}
</script>

<template>
  <div class="rte">
    <div v-if="editor" class="rte__bar">
      <button
        type="button"
        :class="{ on: editor.isActive('bold') }"
        title="굵게"
        @click="editor.chain().focus().toggleBold().run()"
      >
        <b>B</b>
      </button>
      <button
        type="button"
        :class="{ on: editor.isActive('italic') }"
        title="기울임"
        @click="editor.chain().focus().toggleItalic().run()"
      >
        <i>I</i>
      </button>
      <button
        type="button"
        :class="{ on: editor.isActive('strike') }"
        title="취소선"
        @click="editor.chain().focus().toggleStrike().run()"
      >
        <s>S</s>
      </button>
      <span class="rte__sep" />
      <button
        type="button"
        :class="{ on: editor.isActive('heading', { level: 2 }) }"
        title="제목"
        @click="editor.chain().focus().toggleHeading({ level: 2 }).run()"
      >
        H2
      </button>
      <button
        type="button"
        :class="{ on: editor.isActive('heading', { level: 3 }) }"
        title="소제목"
        @click="editor.chain().focus().toggleHeading({ level: 3 }).run()"
      >
        H3
      </button>
      <span class="rte__sep" />
      <button
        type="button"
        :class="{ on: editor.isActive('bulletList') }"
        title="글머리 목록"
        @click="editor.chain().focus().toggleBulletList().run()"
      >
        •
      </button>
      <button
        type="button"
        :class="{ on: editor.isActive('orderedList') }"
        title="번호 목록"
        @click="editor.chain().focus().toggleOrderedList().run()"
      >
        1.
      </button>
      <button
        type="button"
        :class="{ on: editor.isActive('blockquote') }"
        title="인용"
        @click="editor.chain().focus().toggleBlockquote().run()"
      >
        ❝
      </button>
      <button
        type="button"
        :class="{ on: editor.isActive('codeBlock') }"
        title="코드 블록"
        @click="editor.chain().focus().toggleCodeBlock().run()"
      >
        &lt;/&gt;
      </button>
      <button type="button" :class="{ on: editor.isActive('link') }" title="링크" @click="setLink">
        🔗
      </button>
      <span class="rte__sep" />
      <button type="button" title="실행 취소" @click="editor.chain().focus().undo().run()">
        ↺
      </button>
      <button type="button" title="다시 실행" @click="editor.chain().focus().redo().run()">
        ↻
      </button>
    </div>
    <EditorContent :editor="editor" class="rte__body" />
  </div>
</template>

<style scoped>
.rte {
  border: 1px solid var(--border);
  border-radius: var(--radius-sm, 8px);
  overflow: hidden;
  background: var(--surface, #fff);
}
.rte__bar {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  padding: 6px;
  border-bottom: 1px solid var(--border);
  background: var(--surface-2, #fbfbfc);
}
.rte__bar button {
  min-width: 30px;
  height: 30px;
  padding: 0 6px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--ink);
  font-size: 13px;
  cursor: pointer;
}
.rte__bar button:hover {
  background: var(--border);
}
.rte__bar button.on {
  background: var(--brand, #4f46e5);
  color: #fff;
}
.rte__sep {
  width: 1px;
  margin: 4px 4px;
  background: var(--border);
}
.rte__body {
  padding: 12px 14px;
  min-height: 220px;
  max-height: 460px;
  overflow-y: auto;
}
</style>

<style>
/* EditorContent 내부는 scoped 로 안 잡혀서 전역 (rte__body 하위로 한정) */
.rte__body .ProseMirror {
  outline: none;
  min-height: 200px;
  line-height: 1.6;
  color: var(--ink);
}
.rte__body .ProseMirror > * + * {
  margin-top: 0.6em;
}
.rte__body .ProseMirror h2 {
  font-size: 1.3em;
}
.rte__body .ProseMirror h3 {
  font-size: 1.12em;
}
.rte__body .ProseMirror ul,
.rte__body .ProseMirror ol {
  padding-left: 1.4em;
}
.rte__body .ProseMirror blockquote {
  border-left: 3px solid var(--border);
  padding-left: 12px;
  color: var(--muted, #667);
}
.rte__body .ProseMirror pre {
  background: var(--surface-2, #f4f4f6);
  border-radius: 6px;
  padding: 10px 12px;
  overflow-x: auto;
}
.rte__body .ProseMirror p.is-editor-empty:first-child::before {
  content: attr(data-placeholder);
  float: left;
  color: var(--muted, #99a);
  pointer-events: none;
  height: 0;
}
</style>
