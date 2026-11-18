# scroll-craft engine (vendored)

Nguồn: https://github.com/nateherkai/scroll-craft (`skills/scroll-craft/engine`, MIT).

- `scrollcraft.js`: nguyên bản, chỉ được nạp động bởi `src/screens/landing/Landing.tsx`.
- `scrollcraft.css`: giữ token `--sc-*` và phần DEVICES; các reset toàn cục (`*`, `html`, `body`,
  `::selection`, `:focus-visible`, `a`) được thu hẹp vào `.sc-root` để không đè lên giao diện ứng dụng.
- Khi rời trang giới thiệu: gọi `destroy()` và xoá `--sc-canvas` inline trên `<html>`.
