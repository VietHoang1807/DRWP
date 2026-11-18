/**
 * Shared platform (tenancy, persistence, messaging, security, web). Khai báo OPEN để mọi module
 * nghiệp vụ dùng được các package con; platform KHÔNG được phụ thuộc ngược vào module nghiệp vụ
 * (kiểm tra bằng ArchUnit trong business-app).
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Platform",
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package vn.drwp.platform;
