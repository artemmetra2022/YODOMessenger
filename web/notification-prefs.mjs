/** Local, account-scoped preferences. No content is sent to a third party. */
export const defaultNotifications = () => ({
  enabled: false,
  sound: false,
  previews: false,
  quietUntil: 0,
  chats: {},
});
const until = (x) =>
  typeof x === "number" && Number.isFinite(x) && (x === -1 || x >= 0) ? x : 0;
export function normalizeNotifications(raw) {
  const d = defaultNotifications();
  if (!raw || typeof raw !== "object") return d;
  return {
    ...d,
    enabled: raw.enabled === true,
    sound: raw.sound === true,
    previews: raw.previews === true,
    quietUntil: until(raw.quietUntil),
    chats:
      raw.chats && typeof raw.chats === "object"
        ? Object.fromEntries(
            Object.entries(raw.chats)
              .slice(0, 2000)
              .map(([k, v]) => [k, { muteUntil: until(v?.muteUntil) }]),
          )
        : {},
  };
}
export const isMuted = (untilValue, now = Date.now()) =>
  untilValue === -1 || untilValue > now;
export function shouldNotify(
  p,
  { chatId, visible, activeChatId, senderId, owner, silent = false },
  now = Date.now(),
) {
  return (
    !!owner &&
    p.enabled &&
    !silent &&
    senderId !== owner &&
    !isMuted(p.quietUntil, now) &&
    !isMuted(p.chats[chatId]?.muteUntil || 0, now) &&
    !(visible && activeChatId === chatId)
  );
}
export function notificationText(p, { body, protectedText = false }) {
  return !p.previews || protectedText
    ? "Новое сообщение"
    : String(body || "Новое сообщение").slice(0, 240);
}
export function durationUntil(value, now = Date.now()) {
  if (value === "forever") return -1;
  if (value === "off") return 0;
  const n = Number(value);
  return [3600000, 28800000, 86400000].includes(n) ? now + n : 0;
}
export function remainingMute(value, now = Date.now()) {
  if (value === -1) return "Без срока";
  if (value > now)
    return (
      "До " +
      new Date(value).toLocaleString("ru-RU", {
        day: "2-digit",
        month: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
      })
    );
  return "Не отключены";
}
