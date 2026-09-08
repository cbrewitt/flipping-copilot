package copilot.model;

import joptsimple.internal.*;
import lombok.*;

import java.util.*;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class CopilotLoginState {

    public LoginResponse loginResponse = null;
    public Map<String,Integer> displayNameToAccountId = new HashMap<>();
    public Map<Integer, String> accountIdToDisplayName = new HashMap<>();

    public int getUserId() { return loginResponse != null ? loginResponse.userId : -1; }

    public boolean isLoggedIn() { return loginResponse != null && !Strings.isNullOrEmpty(loginResponse.jwt); }

    public Set<Integer> accountIds() { return accountIdToDisplayName.keySet(); }

    public String getJwtToken() { return !isLoggedIn() ? null : loginResponse.jwt; }

    public Integer getAccountId(String displayName) {
        if (displayName == null) { return null; }
        return displayNameToAccountId.getOrDefault(displayName, -1);
    }

    public String getDisplayName(Integer accountId) {
        if (accountId == null) { return null; }
        return accountIdToDisplayName.getOrDefault(accountId, "Unknown");
    }

    public CopilotLoginState copy() {
        return new CopilotLoginState(loginResponse, new HashMap<>(displayNameToAccountId), new HashMap<>(accountIdToDisplayName));
    }
}
