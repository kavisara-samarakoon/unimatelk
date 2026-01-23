package com.unimatelk.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "preferences")
public class Preference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name="user_id", unique = true, nullable = false)
    private AppUser user;

    // 1-5 scales (simple)
    private Integer sleepSchedule;   // 1 early .. 5 late
    private Integer cleanliness;     // 1 low .. 5 high
    private Integer noiseTolerance;  // 1 quiet .. 5 loud ok
    private Integer guests;          // 1 never .. 5 often ok
    private Boolean smokingOk;
    private Boolean drinkingOk;
    private Integer introvert;       // 1 intro .. 5 extro

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }

    public Integer getSleepSchedule() { return sleepSchedule; }
    public void setSleepSchedule(Integer sleepSchedule) { this.sleepSchedule = sleepSchedule; }

    public Integer getCleanliness() { return cleanliness; }
    public void setCleanliness(Integer cleanliness) { this.cleanliness = cleanliness; }

    public Integer getNoiseTolerance() { return noiseTolerance; }
    public void setNoiseTolerance(Integer noiseTolerance) { this.noiseTolerance = noiseTolerance; }

    public Integer getGuests() { return guests; }
    public void setGuests(Integer guests) { this.guests = guests; }

    public Boolean getSmokingOk() { return smokingOk; }
    public void setSmokingOk(Boolean smokingOk) { this.smokingOk = smokingOk; }

    public Boolean getDrinkingOk() { return drinkingOk; }
    public void setDrinkingOk(Boolean drinkingOk) { this.drinkingOk = drinkingOk; }

    public Integer getIntrovert() { return introvert; }
    public void setIntrovert(Integer introvert) { this.introvert = introvert; }
}
