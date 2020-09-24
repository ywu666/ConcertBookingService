package se325.assignment01.concert.service.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import javax.persistence.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;


@Entity
@Table(name = "CONCERTS")
public class Concert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="ID",nullable = false, unique = true)
    private Long id;

    @Column(name="TITLE")
    private String title;

    @Column(name="IMAGE_NAME")
    private String imageName;

    @Column(name="BLURB", length=1024)
    private String blurb;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "CONCERT_DATES")
    @Column(name="DATE")
    private Set<LocalDateTime> dates;

    @ManyToMany(cascade = CascadeType.PERSIST)
    @org.hibernate.annotations.Fetch(
            org.hibernate.annotations.FetchMode.SUBSELECT)
    @JoinTable(name = "CONCERT_PERFORMER",
            joinColumns = @JoinColumn(name="CONCERT_ID", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "PERFORMER_ID", referencedColumnName = "id"))
    @Column(name="PERFORMER")
    private List<Performer> performers;

    public Concert(){}

    public Concert(Long id, String title, String imageName, String blurb, List<Performer> performers){
        this.id = id;
        this.title = title;
        this.imageName = imageName;
        this.blurb = blurb;
        this.performers = performers;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getImageName() {
        return imageName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public String getBlurb() {
        return blurb;
    }

    public void setBlurb(String blurb) {
        this.blurb = blurb;
    }

    public Set<LocalDateTime> getDates() {
        return dates;
    }

    public void setDates(Set<LocalDateTime> dates) {
        this.dates = dates;
    }

    public List<Performer> getPerformers() {
        return performers;
    }

    public void setPerformers(List<Performer> performers) {
        this.performers = performers;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Concert))
            return false;
        if (obj == this)
            return true;

        Concert rhs = (Concert) obj;

        return new EqualsBuilder().
                append(title, rhs.title).
                isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 31).
                append(title).hashCode();
    }
}
