package com.findtheblock.party;

import com.findtheblock.FindTheBlock;
import com.findtheblock.util.Chat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PartyManager {

    private final FindTheBlock plugin;
    private final Map<UUID, Party> memberPartyMap = new ConcurrentHashMap<>();
    private final Map<UUID, Party> partiesByLeader = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingInvites = new ConcurrentHashMap<>();

    public PartyManager(FindTheBlock plugin) {
        this.plugin = plugin;
        startInactivityChecker();
    }

    private void startInactivityChecker() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkInactivity, 1200L, 1200L);
    }

    private void checkInactivity() {
        int timeoutMinutes = plugin.getConfigManager().getLeaderInactivityMinutes();
        if (timeoutMinutes <= 0) return;

        long threshold = System.currentTimeMillis() - (timeoutMinutes * 60_000L);
        for (Party party : new ArrayList<>(partiesByLeader.values())) {
            UUID leaderUuid = party.getLeaderUuid();
            Player leader = Bukkit.getPlayer(leaderUuid);

            boolean isLeaderOffline = leader == null || !leader.isOnline();
            if (party.getLastActivity() < threshold || isLeaderOffline) {
                UUID newLeaderUuid = null;
                Player newLeaderPlayer = null;

                for (UUID memberUuid : party.getMembers()) {
                    if (!memberUuid.equals(leaderUuid)) {
                        Player p = Bukkit.getPlayer(memberUuid);
                        if (p != null && p.isOnline()) {
                            newLeaderUuid = memberUuid;
                            newLeaderPlayer = p;
                            break;
                        }
                    }
                }

                if (newLeaderUuid != null && newLeaderPlayer != null) {
                    partiesByLeader.remove(leaderUuid);
                    party.setLeader(newLeaderUuid, newLeaderPlayer.getName());
                    partiesByLeader.put(newLeaderUuid, party);

                    broadcastToParty(party, "<yellow>Party leader was inactive. <gold>" + newLeaderPlayer.getName() + "</gold> is now the party leader.</yellow>");
                } else if (isLeaderOffline) {
                    disbandParty(leaderUuid);
                }
            }
        }
    }

    public boolean isPartyLeader(UUID uuid) {
        Party party = memberPartyMap.get(uuid);
        return party != null && party.getLeaderUuid().equals(uuid);
    }

    public Party getParty(UUID uuid) {
        return memberPartyMap.get(uuid);
    }

    public Party getPartyByLeader(UUID leaderUuid) {
        return partiesByLeader.get(leaderUuid);
    }

    public UUID getPartyLeader(UUID uuid) {
        Party party = memberPartyMap.get(uuid);
        return party != null ? party.getLeaderUuid() : null;
    }

    public Set<UUID> getPartyMembers(UUID leaderUuid) {
        Party party = partiesByLeader.get(leaderUuid);
        return party != null ? party.getMembers() : Collections.singleton(leaderUuid);
    }

    public Set<UUID> getPlayerPartyMembers(UUID playerUuid) {
        Party party = memberPartyMap.get(playerUuid);
        return party != null ? party.getMembers() : Collections.singleton(playerUuid);
    }

    public List<Party> getPublicParties() {
        List<Party> list = new ArrayList<>();
        for (Party p : partiesByLeader.values()) {
            if (p.isPublic() && !p.isFull()) {
                list.add(p);
            }
        }
        return list;
    }

    public boolean createParty(Player leader, boolean isPublic) {
        UUID uuid = leader.getUniqueId();
        if (memberPartyMap.containsKey(uuid)) {
            Chat.send(leader, "<red>You are already in a party.</red>");
            return false;
        }

        Party party = new Party(uuid, leader.getName(), isPublic, 8);
        partiesByLeader.put(uuid, party);
        memberPartyMap.put(uuid, party);

        String visibility = isPublic ? "<green>public</green>" : "<yellow>private</yellow>";
        Chat.send(leader, "<gray>Party created (" + visibility + "<gray>).</gray>");
        return true;
    }

    public void setPublic(Player leader, boolean isPublic) {
        UUID uuid = leader.getUniqueId();
        Party party = partiesByLeader.get(uuid);
        if (party == null) {
            Chat.send(leader, "<red>You are not the party leader.</red>");
            return;
        }
        party.setPublic(isPublic);
        String visibility = isPublic ? "<green>public</green>" : "<yellow>private</yellow>";
        broadcastToParty(party, "<gray>Party privacy set to " + visibility + "<gray>.</gray>");
    }

    public void joinPublicParty(Player player, UUID leaderUuid) {
        UUID uuid = player.getUniqueId();
        if (memberPartyMap.containsKey(uuid)) {
            Chat.send(player, "<red>Leave your current party first.</red>");
            return;
        }

        Party party = partiesByLeader.get(leaderUuid);
        if (party == null || !party.isPublic()) {
            Chat.send(player, "<red>That party is no longer available.</red>");
            return;
        }

        if (party.isFull()) {
            Chat.send(player, "<red>That party is full.</red>");
            return;
        }

        party.addMember(uuid);
        memberPartyMap.put(uuid, party);

        Chat.send(player, "<gray>Joined <yellow>" + party.getLeaderName() + "'s</yellow> party.</gray>");
        broadcastToParty(party, "<gray><yellow>" + player.getName() + "</yellow> joined the party.</gray>");
    }

    public void invitePlayer(Player leader, Player target) {
        UUID leaderUuid = leader.getUniqueId();
        Party party = partiesByLeader.get(leaderUuid);
        if (party == null) {
            Chat.send(leader, "<red>Only the party leader can invite players.</red>");
            return;
        }

        if (party.isFull()) {
            Chat.send(leader, "<red>Party is full.</red>");
            return;
        }

        if (memberPartyMap.containsKey(target.getUniqueId())) {
            Chat.send(leader, "<red>" + target.getName() + " is already in a party.</red>");
            return;
        }

        pendingInvites.put(target.getUniqueId(), leaderUuid);
        party.touch();

        Chat.send(leader, "<gray>Invited <yellow>" + target.getName() + "</yellow> to the party.</gray>");
        Chat.send(target, "<yellow>" + leader.getName() + "</yellow> <gray>invited you to a party.</gray> " +
                          "<green><click:run_command:/ftb party accept>[Accept]</click></green> " +
                          "<red><click:run_command:/ftb party deny>[Decline]</click></red>");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingInvites.remove(target.getUniqueId(), leaderUuid)) {
                Chat.send(target, "<gray>Party invite from <yellow>" + leader.getName() + "</yellow> expired.</gray>");
            }
        }, 600L);
    }

    public void acceptInvite(Player player) {
        UUID uuid = player.getUniqueId();
        UUID leaderUuid = pendingInvites.remove(uuid);

        if (leaderUuid == null || !partiesByLeader.containsKey(leaderUuid)) {
            Chat.send(player, "<red>You have no pending party invites.</red>");
            return;
        }

        if (memberPartyMap.containsKey(uuid)) {
            Chat.send(player, "<red>You are already in a party.</red>");
            return;
        }

        Party party = partiesByLeader.get(leaderUuid);
        if (party == null || party.isFull()) {
            Chat.send(player, "<red>This party is full or no longer exists.</red>");
            return;
        }

        party.addMember(uuid);
        memberPartyMap.put(uuid, party);

        Chat.send(player, "<gray>Joined <yellow>" + party.getLeaderName() + "'s</yellow> party.</gray>");
        broadcastToParty(party, "<gray><yellow>" + player.getName() + "</yellow> joined the party.</gray>");
    }

    public void denyInvite(Player player) {
        UUID leaderUuid = pendingInvites.remove(player.getUniqueId());
        if (leaderUuid != null) {
            Chat.send(player, "<gray>Declined party invite.</gray>");
            Player leader = Bukkit.getPlayer(leaderUuid);
            if (leader != null) {
                Chat.send(leader, "<gray><yellow>" + player.getName() + "</yellow> declined your party invite.</gray>");
            }
        } else {
            Chat.send(player, "<red>No pending invites to decline.</red>");
        }
    }

    public void handlePlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        Party party = memberPartyMap.get(uuid);
        if (party == null) return;

        if (party.getLeaderUuid().equals(uuid)) {
            // Transfer leadership to the first available online member
            UUID nextLeaderUuid = null;
            Player nextLeader = null;
            for (UUID memberId : party.getMembers()) {
                if (!memberId.equals(uuid)) {
                    Player p = Bukkit.getPlayer(memberId);
                    if (p != null && p.isOnline()) {
                        nextLeaderUuid = memberId;
                        nextLeader = p;
                        break;
                    }
                }
            }

            if (nextLeaderUuid != null && nextLeader != null) {
                partiesByLeader.remove(uuid);
                memberPartyMap.remove(uuid);
                party.removeMember(uuid);

                party.setLeader(nextLeaderUuid, nextLeader.getName());
                partiesByLeader.put(nextLeaderUuid, party);

                broadcastToParty(party, "<yellow>Party leader disconnected. <gold>" + nextLeader.getName() + "</gold> is now the party leader.</yellow>");
            } else {
                disbandParty(uuid);
            }
        } else {
            leaveParty(player);
        }
    }

    public void leaveParty(Player player) {
        UUID uuid = player.getUniqueId();
        Party party = memberPartyMap.remove(uuid);

        if (party == null) {
            Chat.send(player, "<red>You are not in a party.</red>");
            return;
        }

        if (party.getLeaderUuid().equals(uuid)) {
            disbandParty(uuid);
        } else {
            party.removeMember(uuid);
            Chat.send(player, "<gray>You left the party.</gray>");
            broadcastToParty(party, "<gray><yellow>" + player.getName() + "</yellow> left the party.</gray>");
        }
    }

    public void disbandParty(UUID leaderUuid) {
        Party party = partiesByLeader.remove(leaderUuid);
        if (party != null) {
            for (UUID memberUuid : party.getMembers()) {
                memberPartyMap.remove(memberUuid);
                Player p = Bukkit.getPlayer(memberUuid);
                if (p != null) {
                    Chat.send(p, "<red>Party disbanded.</red>");
                }
            }
        }
    }

    public void broadcastToParty(Party party, String message) {
        if (party == null) return;
        for (UUID memberUuid : party.getMembers()) {
            Player p = Bukkit.getPlayer(memberUuid);
            if (p != null && p.isOnline()) {
                Chat.send(p, message);
            }
        }
    }
}
