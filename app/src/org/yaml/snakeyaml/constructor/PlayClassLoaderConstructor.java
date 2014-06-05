package org.yaml.snakeyaml.constructor;

public class PlayClassLoaderConstructor extends Constructor {
  private ClassLoader loader = PlayClassLoaderConstructor.class.getClassLoader();

  public PlayClassLoaderConstructor (ClassLoader cLoader) {
    this(Object.class, cLoader);
  }

  public PlayClassLoaderConstructor(Class<? extends Object> theRoot) {
    super(theRoot);
    ClassLoader l = theRoot.getClassLoader();
    if (l == null) {
      throw new NullPointerException("Unable to get ClassLoader from root Object");
    }
    this.loader = l;
  }

  public PlayClassLoaderConstructor(Class<? extends Object> theRoot, ClassLoader theLoader) {
    super(theRoot);
    if (theLoader == null) {
      throw new NullPointerException("Loader must be provided.");
    }
    this.loader = theLoader;
  }

  @Override
  protected Class<?> getClassForName(String name) throws ClassNotFoundException {
    return Class.forName(name, true, loader);
  }
}
