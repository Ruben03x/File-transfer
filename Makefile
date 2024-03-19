MVN = mvn
MVN_FLAGS = -B

# Define targets and dependencies
.PHONY: clean compile run-sender run-receiver

# Build target
build:
	$(MVN) $(MVN_FLAGS) clean install

# Compile target
compile:
	$(MVN) $(MVN_FLAGS) compile

# Run sender GUI target
run-sender:
	$(MVN) $(MVN_FLAGS) javafx:run -Psender

# Run receiver GUI target
run-receiver:
	$(MVN) $(MVN_FLAGS) javafx:run -Preceiver

# Clean target
clean:
	$(MVN) $(MVN_FLAGS) clean

