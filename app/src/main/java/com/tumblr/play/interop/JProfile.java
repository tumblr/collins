package com.tumblr.play.interop;

import java.util.List;
import java.util.ArrayList;

public class JProfile implements java.io.Serializable {
    static final long serialVersionUID = -3055921821909895808L;
    private String label;
    private String prefix;
    private String primary_role;
    private String secondary_role;
    private String pool;
    private String contact;
    private String contact_notes;
    private List<String> allowed_classes;

    private Boolean allow_suffix;
    private Boolean requires_primary_role;
    private Boolean requires_secondary_role;
    private Boolean requires_pool;

    public JProfile() {
        label = "";
        prefix = "";
        primary_role = "";
        secondary_role = "";
        pool = "";
        allowed_classes= new ArrayList<String>();
        allow_suffix = Boolean.FALSE;
        requires_primary_role = Boolean.TRUE;
        requires_secondary_role = Boolean.FALSE;
        requires_pool = Boolean.TRUE;
    }

    public String getLabel() {
        return label;
    }
    public void setLabel(final String label) {
        this.label = label;
    }

    public String getPrefix() {
        return prefix;
    }
    public void setPrefix(final String prefix) {
        this.prefix = prefix;
    }

    public String getPrimary_role() {
        return primary_role;
    }
    public void setPrimary_role(final String primary_role) {
        this.primary_role = primary_role;
    }

    public String getContact() {
        return contact;
    }
    public void setContact(final String contact) {
        this.contact = contact;
    }

    public String getContact_notes() {
        return contact_notes;
    }
    public void setContact_notes(final String contact_notes) {
        this.contact_notes = contact_notes;
    }

    public String getSecondary_role() {
        return secondary_role;
    }
    public void setSecondary_role(final String secondary_role) {
        this.secondary_role = secondary_role;
    }

    public String getPool() {
        return pool;
    }
    public void setPool(final String pool) {
        this.pool = pool;
    }

    public Boolean getAllow_suffix() {
        return allow_suffix;
    }
    public void setAllow_suffix(final Boolean allow_suffix) {
        this.allow_suffix = allow_suffix;
    }

    public Boolean getRequires_primary_role() {
        return requires_primary_role;
    }
    public void setRequires_primary_role(final Boolean requires_primary_role) {
        this.requires_primary_role = requires_primary_role;
    }

    public Boolean getRequires_secondary_role() {
        return requires_secondary_role;
    }
    public void setRequires_secondary_role(final Boolean requires_secondary_role) {
        this.requires_secondary_role = requires_secondary_role;
    }

    public Boolean getRequires_pool() {
        return requires_pool;
    }
    public void setRequires_pool(final Boolean requires_pool) {
        this.requires_pool = requires_pool;
    }

    public List<String> getAllowed_classes() {
        return allowed_classes;
    }
    public void setAllowed_classes(final List<String> allowed_classes) {
        this.allowed_classes = allowed_classes;
    }

}
