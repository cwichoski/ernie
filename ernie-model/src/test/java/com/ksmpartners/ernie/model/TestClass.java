package com.ksmpartners.ernie.model;

/**
 * Class used for testing serialization and deserialization
 */
public class TestClass {

    private String name;
    private int id;

    // Must have public no-arg constructors
    public TestClass(){}

    public TestClass(String name, int id)
    {
        this.name = name;
        this.id = id;
    }

    // Field names in JSON are taken from getter names, unless annotated with @JsonProperty("Alternate Name")
    public String getName()
    {
        return name;
    }

    public int getId()
    {
        return id;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public void setId(int id)
    {
        this.id = id;
    }

    // This is for testing purposes
    @Override
    public boolean equals(Object another)
    {
        if(!(another instanceof TestClass))
            return false;

        TestClass that = (TestClass) another;

        if(getId() != that.getId())
            return false;
        else if(!getName().equalsIgnoreCase(that.getName()))
            return false;

        return true;
    }

    @Override
    public String toString()
    {
        return "id: " + id + ", name: " + name;
    }

}