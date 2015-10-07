fun foo(s: String): String? {
  try {
    Integer.parseInt(s);
    return "no message";
  }
  catch(e: NumberFormatException) {
    return e.getMessage(); // Work around an overload-resolution bug
  }
}
