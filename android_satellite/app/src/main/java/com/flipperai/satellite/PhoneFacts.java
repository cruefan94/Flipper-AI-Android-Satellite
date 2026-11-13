package com.flipperai.satellite;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Explicit, narrow queries about this phone are answered from Android facts.
 * No cloud connection or AI model is required. No device identifiers are exposed.
 * The caller supplies a snapshot; this class is independently JVM-testable.
 */
public final class PhoneFacts {
  public enum Connection { WIFI, CELLULAR, OTHER, NONE, UNKNOWN }
  public enum Query { NONE, TIME, DATE, DATE_TIME, BATTERY, CONNECTION }

  public static final class Snapshot {
    public final ZonedDateTime now;
    public final int batteryPercent;
    public final boolean charging;
    public final Connection connection;
    public final boolean internetValidated;

    public Snapshot(ZonedDateTime now, int batteryPercent, boolean charging,
                    Connection connection, boolean internetValidated) {
      if (now == null || connection == null) throw new IllegalArgumentException("Phone snapshot incomplete");
      this.now=now;
      this.batteryPercent=batteryPercent;
      this.charging=charging;
      this.connection=connection;
      this.internetValidated=internetValidated;
    }
  }

  private PhoneFacts() {}

  /** Deliberately reject place-specific or ambiguous prompts; send those to the selected AI. */
  public static Query classify(String prompt) {
    if (prompt == null) return Query.NONE;
    String s=prompt.toLowerCase(Locale.ROOT).replace('’','\'').trim()
        .replaceAll("[?!.]+$", "").replaceAll("\\s+", " ");
    if (s.matches("(?:what(?:'s| is|s) (?:the )?(?:current )?date and time"
          +"|what(?:'s| is|s) (?:the )?(?:current )?time and date"
          +"|(?:current|today's) date and time|date and time)(?: (?:now|here|on my phone))?"))
      return Query.DATE_TIME;
    if (s.matches("(?:what(?:'s| is|s) (?:the )?(?:current )?time"
          +"|what time is it|tell me (?:the )?(?:current )?time"
          +"|(?:current|local|phone) time|time now)(?: (?:now|here|on my phone))?"))
      return Query.TIME;
    if (s.matches("(?:what(?:'s| is|s) (?:today's |the )?(?:current )?date"
          +"|what day is it|what date is it|today's date|(?:current|phone) date"
          +"|date today|today)"))
      return Query.DATE;
    if (s.matches("(?:what(?:'s| is|s) (?:my |the )?(?:phone(?:'s)? )?battery(?: level| percentage| percent)?"
          +"|(?:my )?(?:phone )?battery(?: level| percentage| percent)"
          +"|how much battery (?:do i have|is left|does my phone have)"
          +"|how (?:charged|full) is my phone"
          +"|what(?:'s| is|s) (?:my )?battery percentage)"))
      return Query.BATTERY;
    if (s.matches("(?:am i (?:online|connected to (?:the )?internet|on wi-?fi|using mobile data)"
          +"|is my phone (?:online|connected to (?:the )?internet|on wi-?fi)"
          +"|what(?:'s| is|s) my (?:phone )?(?:internet|network|connection) status"
          +"|(?:phone )?(?:network|internet|connection) status)"))
      return Query.CONNECTION;
    return Query.NONE;
  }

  /** Returns null if the user did not request a recognized on-phone fact. */
  public static String answer(String prompt, Snapshot snapshot) {
    Query query=classify(prompt);
    if (query == Query.NONE) return null;
    if (snapshot == null) throw new IllegalArgumentException("Phone snapshot is required");
    DateTimeFormatter time=DateTimeFormatter.ofPattern("h:mm a z", Locale.ENGLISH);
    DateTimeFormatter date=DateTimeFormatter.ofPattern("EEEE, MMMM d, uuuu", Locale.ENGLISH);
    switch (query) {
      case TIME:
        return "The current time on your phone is "+snapshot.now.format(time)+".";
      case DATE:
        return "Today's date on your phone is "+snapshot.now.format(date)+".";
      case DATE_TIME:
        return "Your phone's local date and time is "+snapshot.now.format(date)+" at "
            +snapshot.now.format(time)+".";
      case BATTERY:
        if (snapshot.batteryPercent < 0 || snapshot.batteryPercent > 100)
          return "Android did not provide a battery percentage.";
        return "Your phone's battery is "+snapshot.batteryPercent+"%."
            +(snapshot.charging?" It is charging.":" It is not charging.");
      case CONNECTION:
        switch(snapshot.connection) {
          case WIFI:
            return snapshot.internetValidated
                ?"Your phone is connected by Wi-Fi; Android has validated internet access."
                :"Your phone is connected by Wi-Fi, but internet access is not verified.";
          case CELLULAR:
            return snapshot.internetValidated
                ?"Your phone is using mobile data; Android has validated internet access."
                :"Your phone is using mobile data, but internet access is not verified.";
          case OTHER:
            return snapshot.internetValidated
                ?"Your phone has an active network connection and Android has validated internet access."
                :"Your phone has an active network connection, but internet access is not verified.";
          case NONE:
            return "Android reports no active network connection right now.";
          default:
            return "Android could not determine the current network connection.";
        }
      default:
        return null;
    }
  }
}
