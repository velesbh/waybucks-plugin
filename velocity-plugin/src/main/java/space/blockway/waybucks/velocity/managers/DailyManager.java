package space.blockway.waybucks.velocity.managers;

import org.slf4j.Logger;
import space.blockway.waybucks.velocity.config.WaybucksVelocityConfig;
import space.blockway.waybucks.velocity.database.BalanceRepository;
import space.blockway.waybucks.velocity.database.DailyRepository;
import space.blockway.waybucks.velocity.database.TransactionRepository;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public class DailyManager {

    public enum DailyResult {
        SUCCESS,
        ALREADY_CLAIMED
    }

    public record DailyClaimResult(DailyResult result, long amount, int streak, long nextClaimAt) {}

    private final DailyRepository dailyRepository;
    private final BalanceRepository balanceRepository;
    private final TransactionRepository transactionRepository;
    private final WaybucksVelocityConfig config;
    private final Logger logger;

    public DailyManager(
            DailyRepository dailyRepository,
            BalanceRepository balanceRepository,
            TransactionRepository transactionRepository,
            WaybucksVelocityConfig config,
            Logger logger
    ) {
        this.dailyRepository = dailyRepository;
        this.balanceRepository = balanceRepository;
        this.transactionRepository = transactionRepository;
        this.config = config;
        this.logger = logger;
    }

    public DailyClaimResult claimDaily(UUID uuid, String username) {
        try {
            long now = System.currentTimeMillis();
            long cooldownMillis = config.getDailyCooldownHours() * 60L * 60L * 1000L;

            Optional<DailyRepository.DailyRecord> optRecord = dailyRepository.getDailyRecord(uuid);

            if (optRecord.isPresent()) {
                DailyRepository.DailyRecord record = optRecord.get();
                long nextClaimAt = record.lastClaimedAt() + cooldownMillis;

                // 1. Check if within cooldown
                if (now < nextClaimAt) {
                    return new DailyClaimResult(DailyResult.ALREADY_CLAIMED, 0L, record.streak(), nextClaimAt);
                }

                // 2. Calculate new streak
                long streakWindowMillis = 48L * 60L * 60L * 1000L;
                int newStreak;
                if (now - record.lastClaimedAt() <= streakWindowMillis) {
                    newStreak = record.streak() + 1;
                } else {
                    newStreak = 1;
                }

                // 3. Cap streak
                int streakCap = config.getDailyStreakCap();
                if (newStreak > streakCap) {
                    newStreak = streakCap;
                }

                // 4. Calculate reward
                long reward = config.getDailyBase() + ((long) newStreak * config.getDailyStreakBonus());

                // 5. Add balance
                balanceRepository.addBalance(uuid, reward);

                // 6. Log transaction
                transactionRepository.record("DAILY", uuid, null, reward,
                        "Daily reward claimed, streak=" + newStreak);

                // 7. Upsert daily record
                dailyRepository.upsert(uuid, now, newStreak);

                long nextClaim = now + cooldownMillis;
                return new DailyClaimResult(DailyResult.SUCCESS, reward, newStreak, nextClaim);

            } else {
                // First time claiming — streak starts at 1
                int newStreak = 1;
                long reward = config.getDailyBase() + ((long) newStreak * config.getDailyStreakBonus());

                // Add balance
                balanceRepository.addBalance(uuid, reward);

                // Log transaction
                transactionRepository.record("DAILY", uuid, null, reward,
                        "Daily reward claimed, streak=" + newStreak);

                // Upsert daily record
                dailyRepository.upsert(uuid, now, newStreak);

                long nextClaim = now + cooldownMillis;
                return new DailyClaimResult(DailyResult.SUCCESS, reward, newStreak, nextClaim);
            }

        } catch (SQLException e) {
            logger.error("Failed to process daily claim for {}", uuid, e);
            return new DailyClaimResult(DailyResult.ALREADY_CLAIMED, 0L, 0, 0L);
        }
    }

    /**
     * Returns the number of milliseconds until the player can claim their next daily reward.
     * Returns 0 if they can claim now (or have never claimed).
     */
    public long getTimeUntilNextClaim(UUID uuid) {
        try {
            Optional<DailyRepository.DailyRecord> optRecord = dailyRepository.getDailyRecord(uuid);
            if (optRecord.isEmpty()) {
                return 0L;
            }

            DailyRepository.DailyRecord record = optRecord.get();
            long cooldownMillis = config.getDailyCooldownHours() * 60L * 60L * 1000L;
            long nextClaimAt = record.lastClaimedAt() + cooldownMillis;
            long now = System.currentTimeMillis();

            if (now >= nextClaimAt) {
                return 0L;
            }

            return nextClaimAt - now;

        } catch (SQLException e) {
            logger.error("Failed to get time until next claim for {}", uuid, e);
            return 0L;
        }
    }
}
