interface My

internal class Your: My

// Should we have an error here? Delegate is internal
class His: My by Your()
