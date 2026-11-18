package vn.drwp.identity.api;

import java.util.List;
import java.util.Set;

public interface RoleDirectory {

    Set<String> rolesOf(String subject);

    List<String> subjectsInRole(String roleCode);
}
