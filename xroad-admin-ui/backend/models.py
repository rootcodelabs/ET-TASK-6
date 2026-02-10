from flask_sqlalchemy import SQLAlchemy
from datetime import datetime

db = SQLAlchemy()

class ServiceFieldConfig(db.Model):
    """Configuration for individual service fields"""
    __tablename__ = 'service_field_config'
    
    id = db.Column(db.Integer, primary_key=True)
    service_name = db.Column(db.String(200), nullable=False, index=True)
    field_name = db.Column(db.String(200), nullable=False)
    field_path = db.Column(db.String(500), nullable=False)
    field_type = db.Column(db.String(100))
    selected = db.Column(db.Boolean, default=True)
    sensitive = db.Column(db.Boolean, default=False)
    created_at = db.Column(db.DateTime, default=datetime.utcnow)
    updated_at = db.Column(db.DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    
    __table_args__ = (
        db.UniqueConstraint('service_name', 'field_path', name='uq_service_field'),
    )
    
    def to_dict(self):
        return {
            'id': self.id,
            'service_name': self.service_name,
            'name': self.field_name,
            'path': self.field_path,
            'type': self.field_type,
            'selected': self.selected,
            'sensitive': self.sensitive,
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'updated_at': self.updated_at.isoformat() if self.updated_at else None
        }


class ServiceConfig(db.Model):
    """Configuration for services"""
    __tablename__ = 'service_config'
    
    id = db.Column(db.Integer, primary_key=True)
    service_name = db.Column(db.String(200), unique=True, nullable=False, index=True)
    description = db.Column(db.Text)
    endpoint = db.Column(db.String(500))
    enabled = db.Column(db.Boolean, default=True)
    created_at = db.Column(db.DateTime, default=datetime.utcnow)
    updated_at = db.Column(db.DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    
    def to_dict(self):
        return {
            'id': self.id,
            'service_name': self.service_name,
            'description': self.description,
            'endpoint': self.endpoint,
            'enabled': self.enabled,
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'updated_at': self.updated_at.isoformat() if self.updated_at else None
        }


def init_db(app):
    """Initialize the database"""
    db.init_app(app)
    with app.app_context():
        db.create_all()
