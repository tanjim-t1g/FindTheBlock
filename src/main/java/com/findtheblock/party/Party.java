package com.findtheblock.party;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class Party {

    private UUID leaderUuid;
    private String leaderName;
    private final Set<UUID> members = new LinkedHashSet<>();
    private boolean isPublic;
    private final int maxSize;
    private final long createdAt;
    private long lastActivity;

    public Party(UUID leaderUuid, String leaderName, boolean isPublic, int maxSize) {
        this.leaderUuid = leaderUuid;
        this.leaderName = leaderName;
        this.isPublic = isPublic;
        this.maxSize = maxSize;
        this.createdAt = System.currentTimeMillis();
        this.lastActivity = System.currentTimeMillis();
        this.members.add(leaderUuid);
    }

    public UUID getLeaderUuid() { return leaderUuid; }
    public String getLeaderName() { return leaderName; }
    public void setLeaderName(String leaderName) { this.leaderName = leaderName; }

    public void setLeader(UUID newLeaderUuid, String newLeaderName) {
        this.leaderUuid = newLeaderUuid;
        this.leaderName = newLeaderName;
        touch();
    }

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
        touch();
    }

    public int getMaxSize() { return maxSize; }
    public long getCreatedAt() { return createdAt; }
    public long getLastActivity() { return lastActivity; }
    public void touch() { this.lastActivity = System.currentTimeMillis(); }

    public synchronized Set<UUID> getMembers() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(members));
    }

    public synchronized boolean addMember(UUID uuid) {
        if (members.size() >= maxSize) return false;
        boolean added = members.add(uuid);
        if (added) touch();
        return added;
    }

    public synchronized boolean removeMember(UUID uuid) {
        boolean removed = members.remove(uuid);
        if (removed) touch();
        return removed;
    }

    public synchronized boolean contains(UUID uuid) {
        return members.contains(uuid);
    }

    public synchronized int size() {
        return members.size();
    }

    public boolean isFull() {
        return members.size() >= maxSize;
    }
}
