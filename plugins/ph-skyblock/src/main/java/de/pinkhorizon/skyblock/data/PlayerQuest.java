package de.pinkhorizon.skyblock.data;

import de.pinkhorizon.skyblock.enums.QuestType;

import java.time.LocalDate;

public class PlayerQuest {

    private final String questId;    // QuestType.id + "_" + difficulty (e.g. "cobble_miner_1")
    private final QuestType type;
    private final int difficulty;    // 0=Leicht, 1=Normal, 2=Schwer, 3=Episch
    private long progress;
    private boolean completed;
    private boolean rewardClaimed;
    private boolean notified;
    private LocalDate questDate;

    public PlayerQuest(String questId, QuestType type, int difficulty,
                       long progress, boolean completed, boolean rewardClaimed, LocalDate questDate) {
        this.questId = questId;
        this.type = type;
        this.difficulty = difficulty;
        this.progress = progress;
        this.completed = completed;
        this.rewardClaimed = rewardClaimed;
        this.questDate = questDate;
    }

    public long getGoal()         { return type.getGoal(difficulty); }
    public long getReward()       { return type.getReward(difficulty); }
    public long getScoreReward()  { return type.getScoreReward(difficulty); }
    public String getDiffName()   { return type.getDifficultyName(difficulty); }

    public void addProgress(long amount) {
        if (!completed) {
            progress = Math.min(progress + amount, getGoal());
            if (progress >= getGoal()) completed = true;
        }
    }

    public double getProgressPercent() {
        return getGoal() > 0 ? (double) progress / getGoal() * 100.0 : 0;
    }

    public String getProgressBar() {
        int filled = (int) (getProgressPercent() / 10);
        StringBuilder sb = new StringBuilder("§8[");
        for (int i = 0; i < 10; i++) sb.append(i < filled ? "§a■" : "§7■");
        sb.append("§8]");
        return sb.toString();
    }

    // Getters
    public String getQuestId()       { return questId; }
    public QuestType getType()       { return type; }
    public int getDifficulty()       { return difficulty; }
    public long getProgress()        { return progress; }
    public boolean isCompleted()     { return completed; }
    public boolean isRewardClaimed() { return rewardClaimed; }
    public LocalDate getQuestDate()  { return questDate; }

    public boolean isNotified()            { return notified; }

    public void setProgress(long v)       { this.progress = v; }
    public void setCompleted(boolean v)   { this.completed = v; }
    public void setRewardClaimed(boolean v){ this.rewardClaimed = v; }
    public void setNotified(boolean v)    { this.notified = v; }
}
