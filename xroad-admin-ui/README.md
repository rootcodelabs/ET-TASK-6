# X-Road Admin UI

A prototype application for managing X-Road service fields and testing requests with field-level configuration and sensitive data handling.

## Features

- **Remote Service Discovery**: Automatically fetches services from wsdl URL
- **XSD-Based Input Parameters**: Extracts input parameter definitions from XSD schemas and displays as individual text fields
- **Smart Parameter Handling**: Only sends non-empty parameters to the API 
- **Response Field Configuration**: Select which fields to display with hierarchical tree view
- **Sensitive Data Management**: Mark fields containing sensitive data for masking in logs
- **Expandable JSON Response Viewer**: Interactive response viewer with expand/collapse functionality
- **Docker Support**: Full containerization with multi-stage builds for both frontend and backend

```

## Prerequisites

- Python 3.8 or higher
- Node.js 16 or higher
- npm or yarn

**OR**

- Docker Desktop (for containerized deployment)

## Installation

### Option 1: Docker (Recommended)

The easiest way to run the entire application:

```bash
cd xroad-admin-ui
docker-compose up -d --build
```

This will start:
- **Backend** at http://localhost:5000 (Flask API)
- **Frontend** at http://localhost:3000 (nginx-served React app)

The frontend is built using a multi-stage Docker build and served via nginx with automatic proxy to the backend API.

See [DOCKER.md](DOCKER.md) for detailed Docker instructions.

### Option 2: Manual Installation

### Backend Setup

1. Navigate to the backend directory:
   ```bash
   cd backend
   ```

2. Create a virtual environment (optional but recommended):
   ```bash
   python -m venv venv
   source venv/bin/activate  # On Windows: venv\Scripts\activate
   ```

3. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```

### Frontend Setup

1. Navigate to the frontend directory:
   ```bash
   cd frontend
   ```

2. Install dependencies:
   ```bash
   npm install
   ```

## Running the Application

### Start the Backend

```bash
cd backend
python app.py
```

The backend will start on `http://localhost:5000`

### Start the Frontend

Open a new terminal:

```bash
cd frontend
npm start
```

The frontend will start on `http://localhost:3000` and automatically open in your browser.

## Usage

1. **Select a Service**: Choose from services listed in the remote WSDL (https://ariregxmlv6.rik.ee/?wsdl)
   - Service list is auto-discovered from DSL configuration files
   - Input parameters are automatically extracted from XSD schemas

2. **Configure Response Fields**: 
   - View hierarchical field structure in tree view
   - Select which fields should be visible in responses
   - Mark sensitive fields with the 🔒 Sensitive checkbox (will be masked in logs)
   - Save the configuration to database

3. **Test Service (Optional)**: 
   - Fill in desired input parameters (leave empty fields blank)
   - System only sends non-empty parameters to API
   - Endpoint URL is auto-filled from DSL configuration
   - Click "Send Request" to test

4. **View Response**: 
   - Interactive expandable JSON viewer
   - Use Expand All / Collapse All buttons for navigation
   - Sensitive fields are masked in server logs

### Database

The application uses SQLite with persistent storage via Docker volumes. The database schema includes:

- **ServiceFieldConfig**: Stores field-level configuration
  - `service_name`: Service identifier
  - `field_name`: Field name
  - `field_path`: Hierarchical path (e.g., `keha.seosed.isiku_tyyp`)
  - `selected`: Boolean - whether field is visible in responses
  - `sensitive`: Boolean - whether field should be masked in logs
