package pan.alexander.tordnscrypt.settings.show_rules;

class Rules {

    String text;
    boolean active;
    boolean locked;
    boolean subscription;


    Rules(String text, boolean active, boolean locked, boolean subscription) {
        this.text = text;
        this.active = active;
        this.locked = locked;
        this.subscription = subscription;
    }
}
