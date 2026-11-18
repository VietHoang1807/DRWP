package vn.drwp.metadata.domain;

import vn.drwp.platform.kernel.error.InvalidStateException;

/** DRAFT -> PUBLISHED -> DEPRECATED. Bản PUBLISHED/DEPRECATED bất biến (cũng chặn bằng trigger DB). */
public enum FormVersionStatus {
    DRAFT, PUBLISHED, DEPRECATED;

    public void requireEditable() {
        if (this != DRAFT) {
            throw new InvalidStateException("Only DRAFT versions can be modified (version is " + this + ")");
        }
    }

    public void requireCanPublish() {
        if (this != DRAFT) {
            throw new InvalidStateException("Only DRAFT versions can be published");
        }
    }

    public void requireCanDeprecate() {
        if (this != PUBLISHED) {
            throw new InvalidStateException("Only PUBLISHED versions can be deprecated");
        }
    }
}
