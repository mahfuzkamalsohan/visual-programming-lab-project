package pkg;

public class EnemyAttributeData {
    public String attackType; // "melee" or "ranged"
    public double speed;
    public double attackRange;
    public double aoe; // Area of effect radius
    public double maxHp;

    public EnemyAttributeData(String attackType, double speed, double attackRange, double aoe, double maxHp) {
        this.attackType = attackType;
        this.speed = speed;
        this.attackRange = attackRange;
        this.aoe = aoe;
        this.maxHp = maxHp;
    }
}