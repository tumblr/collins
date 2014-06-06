package com.tumblr.play.interop;

import org.yaml.snakeyaml.constructor.Constructor;

public class CustomClassLoaderConstructor extends Constructor {
  private ClassLoader loader = CustomClassLoaderConstructor.class.getClassLoader();

  public CustomClassLoaderConstructor (ClassLoader cLoader) {
    this(Object.class, cLoader);
  }

  public CustomClassLoaderConstructor(Class<? extends Object> theRoot) {
    super(theRoot);
    ClassLoader l = theRoot.getClassLoader();
    if (l == null) {
      throw new NullPointerException("Unable to get ClassLoader from root Object");
    }
    this.loader = l;
  }

  public CustomClassLoaderConstructor(Class<? extends Object> theRoot, ClassLoader theLoader) {
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
