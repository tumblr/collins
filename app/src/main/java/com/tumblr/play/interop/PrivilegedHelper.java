package com.tumblr.play.interop;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import org.yaml.snakeyaml.Yaml;

public class PrivilegedHelper {
  public static Privileged fromFile(final String filename) throws FileNotFoundException {
    if (filename == null) {
      throw new FileNotFoundException("null obviously could not be found");
    }
    final File file = new File(filename);
    final Yaml yaml = new Yaml(new CustomClassLoaderConstructor(Privileged.class));
    final InputStream fis = new FileInputStream(file);
    Privileged p = null;
    try {
      p = (Privileged) yaml.load(fis);
    } finally {
      try {
        fis.close();
      } catch (Exception e) { }
    }
    return p;
  }
}
