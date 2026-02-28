package com.flippingcopilot.model;

import lombok.*;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.VarbitID;

import java.util.EnumSet;


@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode
@ToString
public class OsrsLoginState {

    public boolean loggedIn;

    // These are not cleared on log out so the last values can still be accessed.
    public boolean isIronMan;
    public EnumSet<WorldType> worldTypes;
    public String displayName;
    public Long accountHash;

    public OsrsLoginState nexState(Client client) {
        if(client.getGameState() == GameState.LOGGED_IN) {
            Player p = client.getLocalPlayer();
            return OsrsLoginState.builder()
                    .loggedIn(true)
                    .isIronMan(client.getVarbitValue(VarbitID.IRONMAN) > 0)
                    .worldTypes(client.getWorldType())
                    .displayName(p == null ? null : p.getName())
                    .accountHash(client.getAccountHash())
                    .build();
        } else {
            return this.toBuilder().loggedIn(false).build();
        }
    }
}
